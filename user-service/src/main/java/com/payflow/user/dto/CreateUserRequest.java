package com.payflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// [H-4] role 필드를 클라이언트가 직접 지정할 수 없도록 제거했다.
// 역할은 서버에서 inviteCode 유무로 결정한다: 코드 없으면 CHILD, 코드 있으면 PARENT.
// 이렇게 하면 악의적인 클라이언트가 PARENT 역할로 가입해 자녀 지갑에 접근하는 경로를 차단한다.
public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10,11}")
        String phoneNumber,

        @NotBlank
        @Size(min = 8)
        String password,

        @NotBlank
        String name,

        // null 또는 생략 시 CHILD로 가입된다.
        // 유효한 초대 코드 제출 시 PARENT로 가입된다.
        @Size(max = 64)
        String inviteCode
) {
}
