package org.example.datn.util;

/**
 * Chuẩn hoá hai định danh của tài xế trước khi lưu và trước khi so trùng.
 *
 * <p>So sánh chuỗi thô sẽ bị lọt: "56D4-324.32", "56D4-32432" và "56d4 324 32"
 * là CÙNG một biển số nhưng khác chuỗi. Mọi đường ghi dữ liệu (đăng ký, nộp lại
 * hồ sơ, cập nhật hồ sơ) đều phải đi qua đây để dữ liệu trong DB đồng nhất và
 * ràng buộc UNIQUE ở tầng CSDL mới thực sự có tác dụng.
 */
public final class ShipperIdentityNormalizer {

    private ShipperIdentityNormalizer() {
    }

    /** CCCD/CMND: chỉ giữ chữ số. Trả {@code null} nếu rỗng để không đụng ràng buộc UNIQUE. */
    public static String normalizeIdCard(String raw) {
        return blankToNull(raw == null ? null : raw.replaceAll("\\D", ""));
    }

    /**
     * Biển số: bỏ mọi ký tự không phải chữ/số rồi viết hoa — khớp đúng với
     * {@code normalizeLicensePlate} bên FE (src/utils/validation.js).
     */
    public static String normalizeLicensePlate(String raw) {
        return blankToNull(raw == null ? null : raw.replaceAll("[^a-zA-Z0-9]", "").toUpperCase());
    }

    /** Chuỗi rỗng/trắng -> {@code null}, còn lại thì cắt khoảng trắng thừa hai đầu. */
    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
