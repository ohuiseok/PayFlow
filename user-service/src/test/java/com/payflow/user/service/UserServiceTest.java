package com.payflow.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.UserResponse;
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
                new CreateUserRequest(" USER@example.com ", "password1234", " User ")
        );

        var user = userRepository.findById(response.userId()).orElseThrow();

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.name()).isEqualTo("User");
        assertThat(passwordEncoder.matches("password1234", user.getPassword())).isTrue();
        assertThat(user.getPassword()).isNotEqualTo("password1234");
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        userService.createUser(new CreateUserRequest("user@example.com", "password1234", "User"));

        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest(" USER@example.com ", "password1234", "Other")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void loginReturnsJwtWithUserIdSubject() {
        UserResponse user = userService.createUser(
                new CreateUserRequest("user@example.com", "password1234", "User")
        );

        var token = userService.login(new LoginRequest("user@example.com", "password1234"));

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token.accessToken())
                .getPayload()
                .getSubject();

        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(86400000);
        assertThat(subject).isEqualTo(String.valueOf(user.userId()));
    }

    @Test
    void loginRejectsLockedUser() {
        UserResponse response = userService.createUser(
                new CreateUserRequest("user@example.com", "password1234", "User")
        );
        var user = userRepository.findById(response.userId()).orElseThrow();
        user.lock();

        assertThatThrownBy(() -> userService.login(new LoginRequest("user@example.com", "password1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginRejectsWrongPassword() {
        userService.createUser(new CreateUserRequest("user@example.com", "password1234", "User"));

        assertThatThrownBy(() -> userService.login(new LoginRequest("user@example.com", "wrong-password")))
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
