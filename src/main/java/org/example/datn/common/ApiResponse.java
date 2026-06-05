package org.example.datn.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.example.datn.Exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * Unified envelope for every API response (success and error).
 * Null fields are omitted so success payloads do not carry {@code errorCode}
 * and vice-versa.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private String errorCode;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder().success(true).data(data).message("Tạo thành công").build();
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .errorCode(code.name())
                .message(message)
                .build();
    }
}
