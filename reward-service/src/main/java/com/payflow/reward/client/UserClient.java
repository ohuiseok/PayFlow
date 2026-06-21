package com.payflow.reward.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", url = "${clients.user-service.url:http://localhost:8081}")
public interface UserClient {

    @GetMapping("/users/internal/{userId}")
    UserResponse getInternalUser(
            @PathVariable Long userId,
            @RequestHeader("X-Internal-Request") boolean internalRequest,
            @RequestHeader("X-Internal-Secret") String internalSecret
    );
}
