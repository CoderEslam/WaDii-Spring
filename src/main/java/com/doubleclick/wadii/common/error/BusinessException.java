package com.doubleclick.wadii.common.error;

/** Thrown by services for every expected business failure; mapped to an {@code ApiResponse} error by the handler. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static BusinessException of(ErrorCode code) {
        return new BusinessException(code);
    }

    public static BusinessException of(ErrorCode code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException notFound(ErrorCode code) {
        return new BusinessException(code);
    }

    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.ACCESS_DENIED, message);
    }

    public static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
