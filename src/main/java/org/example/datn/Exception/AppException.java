package org.example.datn.Exception;

import lombok.Getter;
import org.example.datn.util.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.Map;

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

    /**
     * Lỗi theo từng ô nhập, dạng {tên field -> thông báo}. Cho phép trả về NHIỀU lỗi
     * trong một lần gọi (ví dụ trùng cả CCCD lẫn biển số) thay vì bắt người dùng sửa
     * xong cái này mới lòi ra cái kia. {@code null} với các lỗi không gắn với ô nào.
     */
    private final Map<String, String> fieldErrors;

    public AppException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null, null);
    }

    public AppException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public AppException(ErrorCode errorCode, String message, Integer retryAfterSeconds) {
        this(errorCode, message, retryAfterSeconds, null);
    }

    public AppException(ErrorCode errorCode, String message, Map<String, String> fieldErrors) {
        this(errorCode, message, null, fieldErrors);
    }

    private AppException(ErrorCode errorCode, String message,
                         Integer retryAfterSeconds, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fieldErrors = fieldErrors;
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
