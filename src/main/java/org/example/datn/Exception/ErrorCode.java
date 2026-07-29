package org.example.datn.Exception;

import org.springframework.http.HttpStatus;

/**
 * Centralised catalogue of business error codes. Throw via
 * {@link AppException} instead of scattering ad-hoc String messages.
 */
public enum ErrorCode {

    // Auth
    UNAUTHORIZED(401, "Chưa đăng nhập"),
    FORBIDDEN(403, "Không có quyền truy cập"),
    TOKEN_EXPIRED(401, "Token đã hết hạn"),
    TOKEN_INVALID(401, "Token không hợp lệ"),
    BAD_CREDENTIALS(401, "Sai số điện thoại hoặc mật khẩu"),
    EMAIL_NOT_VERIFIED(403, "Tài khoản chưa xác thực OTP/email"),

    // User
    USER_NOT_FOUND(404, "Không tìm thấy người dùng"),
    PHONE_EXISTS(409, "Số điện thoại đã được đăng ký"),
    EMAIL_EXISTS(409, "Email đã được đăng ký"),
    //Them phan thong bao cho CCCD và Bien so xe neu bi trung hoac da co
    ID_CARD_EXISTS(409, "Số CCCD/CMND đã được đăng ký"),
    LICENSE_PLATE_EXISTS(409, "Biển số xe đã được đăng ký"),

    // Restaurant / Food / Category
    RESTAURANT_NOT_FOUND(404, "Không tìm thấy quán ăn"),
    CATEGORY_NOT_FOUND(404, "Không tìm thấy danh mục"),
    FOOD_NOT_FOUND(404, "Không tìm thấy món ăn"),

    // Cart
    CART_EMPTY(400, "Giỏ hàng đang trống"),
    CART_CONFLICT(409, "Giỏ hàng đang chứa món từ quán khác"),
    CART_ITEM_NOT_FOUND(404, "Không tìm thấy món trong giỏ"),
    CART_NOT_FOUND(404, "không tìm thấy giỏ hàng"),

    // Order
    ORDER_NOT_FOUND(404, "Không tìm thấy đơn hàng"),
    ORDER_STATUS_INVALID(422, "Trạng thái đơn hàng không hợp lệ"),
    ORDER_ALREADY_TAKEN(409, "Đơn đã được shipper khác nhận"),
    ORDER_ALREADY_CANCELLED(409, "Đơn hàng đã được hủy trước đó"),
    ORDER_ALREADY_COMPLETED(400, "Đơn đã hoàn tất, vui lòng dùng chức năng khiếu nại/hoàn tiền"),
    ORDER_CANCEL_STAGE_INVALID(400, "Đơn đang được chuẩn bị/giao, không thể hủy"),
    ORDER_NOT_READY_FOR_PICKUP(409, "Đơn chưa sẵn sàng để nhận"),

    // Review
    REVIEW_NOT_ALLOWED(422, "Không thể đánh giá lúc này"),
    REVIEW_EXISTS(409, "Đơn hàng này đã được đánh giá"),

    // OTP
    OTP_INVALID(400, "Mã OTP không tồn tại"),
    OTP_EXPIRED(400, "Mã OTP đã hết hạn"),
    OTP_WRONG_CODE(400, "Mã OTP không đúng"),
    OTP_LOCKED(429, "Tài khoản tạm khóa do nhập sai nhiều lần"),

    // Shipping / Payment
    DISTANCE_TOO_FAR(400, "Khoảng cách vượt quá phạm vi giao hàng"),
    VNPAY_INVALID_SIGNATURE(400, "Chữ ký VNPay không hợp lệ"),

    SHIPPER_OFFLINE(409, "Tài xế đang ngoại tuyến, không thể nhận đơn"),
    SHIPPER_BUSY(409, "Tài xế đang giao đơn khác"),
    SHIPPER_HAS_ACTIVE_DELIVERY(400, "Không thể offline khi đang có đơn hàng đang giao"),

    ADDRESS_NOT_FOUND(404, "Không tìm thấy địa chỉ"),

    // Generic
    VALIDATION_FAILED(400, "Dữ liệu không hợp lệ"),
    NOT_FOUND(404, "Không tìm thấy tài nguyên"),
    INTERNAL_ERROR(500, "Lỗi hệ thống");

    private final int httpStatus;
    private final String message;

    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return HttpStatus.valueOf(httpStatus);
    }

    public String getMessage() {
        return message;
    }
}
