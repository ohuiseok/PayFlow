package com.payflow.user.controller;

import com.payflow.user.dto.AuthTokenResponse;
import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.UserMeResponse;
import com.payflow.user.dto.UserResponse;
import com.payflow.user.service.UserService;
import com.payflow.user.support.error.BusinessException;
import com.payflow.user.support.error.ErrorCode;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Value("${internal.secret:}")
    private String internalSecret;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        // 회원가입은 공개 API다. 아직 JWT가 없으므로 게이트웨이에서도 인증 예외 경로로 둔다.
        return userService.createUser(request);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        // 로그인 성공 시 user-service가 JWT를 발급하고, 이후 요청은 api-gateway가 이 토큰을 검증한다.
        return userService.login(request);
    }

    @GetMapping("/me")
    public UserMeResponse getMe(@RequestHeader("X-User-Id") Long requestUserId) {
        // X-User-Id는 클라이언트가 직접 신뢰받는 값으로 보내는 것이 아니라,
        // api-gateway가 JWT 검증 후 주입한 내부 헤더라는 전제에서 사용한다.
        return userService.getMe(requestUserId);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        // URL path의 userId와 인증된 사용자 ID를 함께 넘겨 서비스 계층에서 소유권을 검증한다.
        return userService.getUser(userId, requestUserId);
    }
    @GetMapping("/internal/{userId}")
    public UserResponse getInternalUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        validateInternalRequest(internalRequest, requestInternalSecret);
        return userService.getInternalUser(userId);
    }

    private void validateInternalRequest(boolean internalRequest, String requestInternalSecret) {
        if (!internalRequest) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(internalSecret)) {
            return; // 내부 시크릿 미설정 시 개발 환경으로 간주하고 허용
        }
        if (!MessageDigest.isEqual(
                internalSecret.getBytes(StandardCharsets.UTF_8),
                (requestInternalSecret == null ? "" : requestInternalSecret).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
