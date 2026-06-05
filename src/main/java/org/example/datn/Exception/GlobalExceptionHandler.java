package org.example.datn.Exception;

import lombok.extern.slf4j.Slf4j;
import org.example.datn.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<?>> handleAppException(AppException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(code.name())
                        .message(ex.getMessage())
                        .build());
    }

    /** 409 — multi-restaurant cart; FE shows replace-confirm modal. */
    @ExceptionHandler(CartConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleCartConflict(CartConflictException ex) {
        return ResponseEntity.status(409)
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.CART_CONFLICT.name())
                        .message(ex.getMessage())
                        .data(Map.of(
                                "currentRestaurant", ex.getCurrentRestaurantName(),
                                "requiresReplace", true))
                        .build());
    }

    /** 422 — review business rule violated. */
    @ExceptionHandler(ReviewNotAllowedException.class)
    public ResponseEntity<ApiResponse<?>> handleReviewNotAllowed(ReviewNotAllowedException ex) {
        return ResponseEntity.status(422)
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.REVIEW_NOT_ALLOWED.name())
                        .message(ex.getMessage())
                        .build());
    }

    /** 422 — illegal order status transition. */
    @ExceptionHandler(OrderStatusException.class)
    public ResponseEntity<ApiResponse<?>> handleOrderStatus(OrderStatusException ex) {
        return ResponseEntity.status(422)
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.ORDER_STATUS_INVALID.name())
                        .message(ex.getMessage())
                        .build());
    }

    /** 400 — @Valid DTO validation failed; returns per-field messages. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError e : ex.getBindingResult().getFieldErrors()) {
            errors.put(e.getField(), e.getDefaultMessage());
        }
        return ResponseEntity.status(400)
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.VALIDATION_FAILED.name())
                        .message(ErrorCode.VALIDATION_FAILED.getMessage())
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(ErrorCode.BAD_CREDENTIALS.getHttpStatus())
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.BAD_CREDENTIALS.name())
                        .message(ErrorCode.BAD_CREDENTIALS.getMessage())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.FORBIDDEN.name())
                        .message(ErrorCode.FORBIDDEN.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(ApiResponse.builder()
                        .success(false)
                        .errorCode(ErrorCode.INTERNAL_ERROR.name())
                        .message(ErrorCode.INTERNAL_ERROR.getMessage())
                        .build());
    }
}
