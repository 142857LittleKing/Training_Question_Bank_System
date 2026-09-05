package com.questionbank.auth;

import com.questionbank.common.enums.UserRole;

/** 当前登录用户信息(从 JWT 解析) */
public record CurrentUser(Long id, String username, String displayName, UserRole role) {

    public String getName() {
        return displayName != null && !displayName.isBlank() ? displayName : username;
    }
}
