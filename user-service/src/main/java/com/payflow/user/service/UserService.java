package com.payflow.user.service;

import com.payflow.user.auth.JwtTokenProvider;
import com.payflow.user.client.BankingClient;
import com.payflow.user.dto.AuthTokenResponse;
import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.UserMeResponse;
import com.payflow.user.dto.UserResponse;
import com.payflow.user.entity.User;
import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;
import com.payflow.user.repository.UserRepository;
import com.payflow.user.support.error.BusinessException;
import com.payflow.user.support.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    // [H-2] 지갑 생성 재시도는 WalletProvisioningService에 위임한다.
    // @Retryable은 AOP 프록시로 동작하므로 별도 빈에서만 올바르게 작동한다.
    private final WalletProvisioningService walletProvisioningService;
    private final BankingClient bankingClient;

    // [H-4] PARENT 초대 코드. 빈 문자열이면 PARENT 가입이 완전히 차단된다.
    @Value("${registration.parent-invite-code:}")
    private String parentInviteCode;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // 전화번호는 화면에서 010-1234-5678, 01012345678처럼 여러 형태로 들어올 수 있다.
        // DB에는 숫자만 저장해 두면 같은 번호를 다른 형식으로 중복 가입하는 문제를 막기 쉽다.
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());

        // 먼저 애플리케이션 레벨에서 중복을 확인해 빠르게 사용자에게 알려 준다.
        // 단, 동시에 같은 번호로 가입 요청이 들어오면 이 검사만으로는 부족하므로 아래 saveAndFlush의 DB unique 제약도 함께 사용한다.
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // [H-4] 클라이언트가 role을 직접 지정하는 대신 서버가 inviteCode를 검증해 역할을 결정한다.
        // 초대 코드가 일치하면 PARENT, 그 외 모든 경우는 CHILD로 가입된다.
        // 초대 코드가 설정되어 있지 않은 환경에서는 PARENT 가입 자체가 차단된다.
        UserRole role = resolveRole(request.inviteCode());

        // 비밀번호는 절대 원문으로 저장하지 않는다.
        // PasswordEncoder가 단방향 해시로 바꿔 저장하고, 로그인 때는 matches로 원문 입력값과 해시를 비교한다.
        User user = new User(
                phoneNumber,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                role
        );

        try {
            // saveAndFlush를 사용하면 트랜잭션 종료 시점까지 미루지 않고 즉시 INSERT를 실행한다.
            // 그래서 unique 제약 위반을 이 메서드 안에서 잡아 도메인 에러로 바꿔 줄 수 있다.
            User savedUser = userRepository.saveAndFlush(user);
            // [H-2] wallet-service 장애 시 재시도한다. 최종 실패해도 사용자 생성은 롤백하지 않는다.
            // 지갑 미생성 사용자는 @Recover 로그로 확인 후 운영자가 수동 보정한다.
            walletProvisioningService.createWalletWithRetry(savedUser.getId());
            return UserResponse.from(savedUser);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        // 로그인 실패 사유를 "없는 사용자"와 "비밀번호 오류"로 나누어 알려 주면 계정 존재 여부가 노출된다.
        // 그래서 두 경우 모두 INVALID_CREDENTIALS 하나로 응답한다.
        User user = userRepository.findByPhoneNumber(normalizePhoneNumber(request.phoneNumber()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 탈퇴/정지 등 ACTIVE가 아닌 사용자는 비밀번호가 맞아도 로그인할 수 없다.
        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // JWT에는 게이트웨이가 하위 서비스로 전달할 최소 사용자 정보(userId, phoneNumber, role)를 담는다.
        // 하위 서비스는 다시 DB를 조회하지 않고도 X-User-* 헤더를 기준으로 권한을 판단할 수 있다.
        String accessToken = jwtTokenProvider.createToken(user);
        return new AuthTokenResponse(
                accessToken,
                "Bearer",
                jwtTokenProvider.getExpirationMillis(),
                UserResponse.from(user)
        );
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long requestUserId) {
        User user = userRepository.findById(requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean hasBankAccount = bankingClient.hasActiveBankAccount(requestUserId);
        return UserMeResponse.from(user, hasBankAccount);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId, Long requestUserId) {
        // 현재 구현은 "내 정보 조회"만 허용한다.
        // URL의 userId와 JWT에서 온 requestUserId가 다르면 다른 사용자의 정보를 보려는 요청으로 간주한다.
        if (!userId.equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getInternalUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return UserResponse.from(user);
    }

    // [H-4] 초대 코드 검증으로 역할을 결정한다.
    // 초대 코드 미설정(빈 문자열) 또는 코드 불일치 시 CHILD를 반환한다.
    // 상수 시간 비교(MessageDigest.isEqual)를 사용해 타이밍 공격을 방지한다.
    private UserRole resolveRole(String inviteCode) {
        if (!StringUtils.hasText(parentInviteCode) || !StringUtils.hasText(inviteCode)) {
            return UserRole.CHILD;
        }
        byte[] expected = parentInviteCode.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] actual   = inviteCode.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(expected, actual)
                ? UserRole.PARENT
                : UserRole.CHILD;
    }

    private String normalizePhoneNumber(String phoneNumber) {
        // 정규식 \D는 숫자가 아닌 모든 문자를 의미한다.
        // 하이픈, 공백, 괄호 등을 제거해 비교와 unique 제약 기준을 하나로 통일한다.
        return phoneNumber.replaceAll("\\D", "");
    }
}
