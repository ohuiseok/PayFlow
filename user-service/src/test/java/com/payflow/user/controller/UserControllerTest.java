package com.payflow.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.user.dto.CreateUserRequest;
import com.payflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Test
    void createUserReturnsCreated() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "01012345678",
                                  "password": "password1234",
                                  "name": "User",
                                  "inviteCode": "TEST-PARENT-CODE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.name").value("User"))
                .andExpect(jsonPath("$.role").value("PARENT"));
    }

    @Test
    void createUserRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "abc",
                                  "password": "short",
                                  "name": "",
                                  "role": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void loginReturnsTokenWithUser() throws Exception {
        userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", null)
        );

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "01012345678",
                                  "password": "password1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.user.role").value("CHILD"));
    }

    @Test
    void getMeReturnsAuthenticatedUser() throws Exception {
        var user = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", "TEST-PARENT-CODE")
        );

        mockMvc.perform(get("/users/me")
                        .header("X-User-Id", user.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.role").value("PARENT"));
    }

    @Test
    void getUserReturnsForbiddenWhenOwnerMismatch() throws Exception {
        var user = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", "TEST-PARENT-CODE")
        );

        mockMvc.perform(get("/users/{userId}", user.userId())
                        .header("X-User-Id", user.userId() + 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_OWNER_MISMATCH"));
    }

    @Test
    void getInternalUserRequiresInternalSecret() throws Exception {
        var user = userService.createUser(
                new CreateUserRequest("01012345678", "password1234", "User", null)
        );

        mockMvc.perform(get("/users/internal/{userId}", user.userId())
                        .header("X-Internal-Request", true)
                        .header("X-Internal-Secret", "test-internal-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.userId()))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"));

        mockMvc.perform(get("/users/internal/{userId}", user.userId())
                        .header("X-Internal-Request", true)
                        .header("X-Internal-Secret", "wrong"))
                .andExpect(status().isForbidden());
    }
}
