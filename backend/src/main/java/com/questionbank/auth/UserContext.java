package com.questionbank.auth;

import com.questionbank.common.BizException;
import com.questionbank.common.enums.UserRole;

import java.util.Set;

/** 线程级当前用户上下文(由 AuthInterceptor 维护) */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    /** 获取当前登录用户, 未登录抛 401 */
    public static CurrentUser required() {
        CurrentUser u = HOLDER.get();
        if (u == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return u;
    }

    /** 角色校验, 不满足抛 403 */
    public static void requireRole(UserRole... roles) {
        CurrentUser u = required();
        for (UserRole r : roles) {
            if (u.role() == r) {
                return;
            }
        }
        throw BizException.forbidden("无权限执行该操作(需要角色: "
                + java.util.Arrays.stream(roles).map(UserRole::getLabel).reduce((a, b) -> a + "/" + b).orElse("")
                + ")");
    }

    public static boolean isAdmin() {
        CurrentUser u = HOLDER.get();
        return u != null && u.role() == UserRole.ADMIN;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
