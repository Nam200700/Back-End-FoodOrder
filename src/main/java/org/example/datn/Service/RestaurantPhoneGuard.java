package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.RestaurantRegisterRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Chặn trùng số điện thoại quán giữa các đối tác.
 *
 * <p>Có BỐN đường ghi số điện thoại quán: đăng ký đối tác, nộp lại hồ sơ sau khi bị từ chối,
 * tự tạo quán và sửa thông tin quán. Trước đây chỉ mỗi đường đăng ký là có kiểm tra, ba đường
 * còn lại ghi thẳng — nên chỉ cần vào "Cài đặt quán" đổi số là lách được luật.
 *
 * <p>Phải soi CẢ HAI bảng: {@code restaurant_registers} giữ hồ sơ chờ/bị từ chối, còn
 * {@code restaurants} giữ quán đã được duyệt — chỉ tra một bảng là lọt.
 */
@Component
@RequiredArgsConstructor
public class RestaurantPhoneGuard {

    /** Không loại trừ ai — dùng id không bao giờ có thật. */
    private static final Long NO_EXCLUSION = -1L;

    private final RestaurantRepository restaurantRepository;
    private final RestaurantRegisterRepository restaurantRegisterRepository;

    /**
     * @param rawPhone             số điện thoại quán người dùng nhập; null/rỗng thì bỏ qua
     * @param excludeRestaurantId  bỏ qua chính quán đang sửa; null khi tạo mới
     * @param excludeOwnerId       bỏ qua hồ sơ đăng ký của chính chủ quán này; null khi đăng ký mới
     */
    public void ensureUnique(String rawPhone, Long excludeRestaurantId, Long excludeOwnerId) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) return;

        String phone = rawPhone.trim();
        Long excludeRestaurant = excludeRestaurantId != null ? excludeRestaurantId : NO_EXCLUSION;
        Long excludeOwner = excludeOwnerId != null ? excludeOwnerId : NO_EXCLUSION;

        boolean taken = restaurantRepository.existsByPhoneAndRestaurantIdNot(phone, excludeRestaurant)
                || restaurantRegisterRepository.existsByPhoneAndOwner_UserIdNot(phone, excludeOwner);

        if (taken) {
            // Gắn lỗi vào tên ô của form đăng ký/nộp lại hồ sơ; màn "Cài đặt quán" đặt tên ô
            // khác nên bên đó map theo errorCode.
            throw new AppException(ErrorCode.RESTAURANT_PHONE_EXISTS,
                    "Số điện thoại này đã được một quán khác sử dụng!",
                    Map.of("restaurantPhone", "Số điện thoại này đã được một quán khác sử dụng!"));
        }
    }
}
