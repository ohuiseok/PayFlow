package com.payflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.UserResponse;
import com.payflow.user.entity.UserRole;
import com.payflow.user.repository.UserRepository;
import com.payflow.user.support.error.BusinessException;
import com.payflow.user.support.error.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    // 테스트 환경의 초대 코드는 application-test.yml에서 TEST-PARENT-CODE로 설정된다.
    private static final String VALID_INVITE_CODE = "TEST-PARENT-CODE";

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Value("${jwt.secret}") String jwtSecret;

    // ── 회원가입: 역할 결정 ────────────────────────────────────────────────────

    @Test
    void createUserWithValidInviteCodeAssignsParentRole() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", " Parent ", VALID_INVITE_CODE)
        );

        assertThat(response.role()).isEqualTo(UserRole.PARENT);
        assertThat(response.name()).isEqualTo("Parent");
        assertThat(response.phoneNumber()).isEqualTo("01012345678");
    }

    @Test
    void createUserWithoutInviteCodeAssignsChildRole() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "Child", null)
        );

        assertThat(response.role()).isEqualTo(UserRole.CHILD);
    }

    @Test
    void createUserWithWrongInviteCodeAssignsChildRole() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", "WRONG-CODE")
        );

        // 잘못된 코드는 CHILD로 가입된다 (에러가 아님).
        assertThat(response.role()).isEqualTo(UserRole.CHILD);
    }

    @Test
    void createUserEncodesPassword() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", " User ", VALID_INVITE_CODE)
        );

        var user = userRepository.findById(response.userId()).orElseThrow();

        assertThat(passwordEncoder.matches("password1234", user.getPassword())).isTrue();
        assertThat(user.getPassword()).isNotEqualTo("password1234");
    }

    @Test
    void createUserRejectsDuplicatePhoneNumber() {
        userService.createUser(new CreateUserRequest("01012345678", "password1234", "User", VALID_INVITE_CODE));

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "Other", null)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    // ── 로그인 ─────────────────────────────────────────────────────────────────

    @Test
    void loginReturnsJwtWithUserInfoClaimsAndResponse() {
        UserResponse user = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", null)
        );

        var token = userService.login(new LoginRequest("01012345678", "password1234"));

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token.accessToken())
                .getPayload();

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(86400000);
        assertThat(token.user().userId()).isEqualTo(user.userId());
        assertThat(token.user().role()).isEqualTo(UserRole.CHILD);
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(user.userId()));
        assertThat(claims.get("phoneNumber", String.class)).isEqualTo("01012345678");
        assertThat(claims.get("role", String.class)).isEqualTo("CHILD");
    }

    @Test
    void loginRejectsLockedUser() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", VALID_INVITE_CODE)
        );
        var user = userRepository.findById(response.userId()).orElseThrow();
        user.lock();

        assertThatThrownBy(() -> userService.login(new LoginRequest("01012345678", "password1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsWrongPassword() {
        userService.createUser(new CreateUserRequest("01012345678", "password1234", "User", VALID_INVITE_CODE));

        assertThatThrownBy(() -> userService.login(new LoginRequest("01012345678", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    // ── 사용자 조회 ────────────────────────────────────────────────────────────

    @Test
    void getUserRejectsMissingUser() {
        assertThatThrownBy(() -> userService.getUser(999L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void getUserRejectsOwnerMismatch() {
        assertThatThrownBy(() -> userService.getUser(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_OWNER_MISMATCH);
    }
}
