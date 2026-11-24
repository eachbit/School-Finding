package com.project.common;

public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException of400(String msg) {
        return new BizException(400, msg);
    }

    public static BizException of403(String msg) {
        return new BizException(403, msg);
    }

    public static BizException of404(String msg) {
        return new BizException(404, msg);
    }

    public int getCode() {
        return code;
    }
}
