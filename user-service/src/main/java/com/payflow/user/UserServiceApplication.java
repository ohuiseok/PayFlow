package com.payflow.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

// [H-2] @EnableRetry는 @Retryable 어노테이션이 동작하도록 Spring AOP 프록시를 활성화한다.
@EnableRetry
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

