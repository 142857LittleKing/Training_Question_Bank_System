package com.questionbank.common;

/** 业务异常: 携带 httpStatus 便于返回合适的 HTTP 状态码 */
public class BizException extends RuntimeException {

    private final int httpStatus;
    private final int code;

    public BizException(String message) {
        this(400, 400, message);
    }

    public BizException(int httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static BizException bad(String message) {
        return new BizException(400, 400, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, 401, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, 403, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, 404, message);
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public int getCode() {
        return code;
    }
}
