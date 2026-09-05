package com.questionbank.dto;

import com.questionbank.common.enums.UserRole;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/** 认证相关 DTO */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank(message = "用户名不能为空") String username,
                               @NotBlank(message = "密码不能为空") String password) {
    }

    public record LoginResponse(String token, Long id, String username, String displayName,
                                UserRole role, String roleLabel) {
    }

    public record UserView(Long id, String username, String displayName, UserRole role,
                           String roleLabel, boolean enabled, LocalDateTime createdAt) {
    }

    public record ChangePasswordRequest(@NotBlank(message = "原密码不能为空") String oldPassword,
                                        @NotBlank(message = "新密码不能为空") String newPassword) {
    }
}
