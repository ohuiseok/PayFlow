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

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    String jwtSecret;

    @Test
    void createUserEncodesPassword() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", " User ", UserRole.PARENT)
        );

        var user = userRepository.findById(response.userId()).orElseThrow();

        assertThat(response.phoneNumber()).isEqualTo("01012345678");
        assertThat(response.name()).isEqualTo("User");
        assertThat(response.role()).isEqualTo(UserRole.PARENT);
        assertThat(passwordEncoder.matches("password1234", user.getPassword())).isTrue();
        assertThat(user.getPassword()).isNotEqualTo("password1234");
    }

    @Test
    void createUserRejectsDuplicatePhoneNumber() {
        userService.createUser(new CreateUserRequest("01012345678", "password1234", "User", UserRole.PARENT));

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "Other", UserRole.CHILD)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void loginReturnsJwtWithUserInfoClaimsAndResponse() {
        UserResponse user = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", UserRole.CHILD)
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
                new CreateUserRequest("01012345678", "password1234", "User", UserRole.PARENT)
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
        userService.createUser(new CreateUserRequest("01012345678", "password1234", "User", UserRole.PARENT));

        assertThatThrownBy(() -> userService.login(new LoginRequest("01012345678", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

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
