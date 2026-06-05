package org.example.datn.Exception;

import lombok.Getter;

/**
 * Base business exception. Carries an {@link ErrorCode} that maps to an HTTP
 * status and a default message; an override message may be supplied.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
