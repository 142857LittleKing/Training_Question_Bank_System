package com.questionbank.common;

import java.io.Serializable;

/**
 * 统一响应包装: code = 0 成功; 非 0 失败(message 描述)
 */
public record ApiResponse<T>(int code, String message, T data) implements Serializable {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
