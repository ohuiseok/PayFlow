package com.payflow.user.controller;

import com.payflow.user.dto.AuthTokenResponse;
import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.dto.LoginRequest;
import com.payflow.user.dto.UserResponse;
import com.payflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping("/me")
    public UserResponse getMe(@RequestHeader("X-User-Id") Long requestUserId) {
        return userService.getMe(requestUserId);
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(
            @PathVariable Long userId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return userService.getUser(userId, requestUserId);
    }
}
