package com.payflow.user.service;

import com.payflow.user.auth.JwtTokenProvider;
import com.payflow.user.dto.AuthTokenResponse;
import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.UserResponse;
import com.payflow.user.entity.User;
import com.payflow.user.entity.UserStatus;
import com.payflow.user.repository.UserRepository;
import com.payflow.user.support.error.BusinessException;
import com.payflow.user.support.error.ErrorCode;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );

        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createToken(user);
        return new AuthTokenResponse(accessToken, "Bearer", jwtTokenProvider.getExpirationMillis());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId, Long requestUserId) {
        if (!userId.equals(requestUserId)) {
            throw new BusinessException(ErrorCode.RESOURCE_OWNER_MISMATCH);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return UserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
