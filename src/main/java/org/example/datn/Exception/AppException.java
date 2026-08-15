package org.example.datn.Exception;

import lombok.Getter;
import org.example.datn.util.DateTimeUtils;

import java.time.LocalDateTime;

/**
 * Base business exception. Carries an {@link ErrorCode} that maps to an HTTP
 * status and a default message; an override message may be supplied.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Số giây còn phải chờ trước khi thử lại (chỉ có ở các lỗi kiểu "tạm khóa").
     * {@code null} với mọi lỗi khác. GlobalExceptionHandler sẽ đẩy giá trị này ra
     * header {@code Retry-After} và vào body để FE đếm ngược theo thời gian thực.
     */
    private final Integer retryAfterSeconds;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.retryAfterSeconds = null;
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = null;
    }

    public AppException(ErrorCode errorCode, String message, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Lỗi tạm khóa do nhập sai OTP quá số lần cho phép. Gom về một chỗ để 9 điểm ném
     * lỗi trong AuthService/OtpService dùng chung một câu chữ và cùng cách tính thời gian.
     */
    public static AppException otpLocked(LocalDateTime lockUntil) {
        return new AppException(
                ErrorCode.OTP_LOCKED,
                "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau "
                        + DateTimeUtils.humanizeUntil(lockUntil) + ".",
                DateTimeUtils.secondsUntil(lockUntil));
    }
}
