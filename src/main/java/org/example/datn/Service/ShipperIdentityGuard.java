package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.ShipperRegisterRepository;
import org.example.datn.Repository.ShipperRepository;
import org.example.datn.util.ShipperIdentityNormalizer;
import org.springframework.stereotype.Component;

/**
 * Chặn trùng CCCD/CMND và biển số xe giữa các tài xế.
 *
 * <p>Có BA đường ghi định danh tài xế: đăng ký mới, nộp lại hồ sơ sau khi bị từ chối,
 * và cập nhật hồ sơ cá nhân. Trước đây mỗi đường kiểm tra một kiểu (thậm chí có đường
 * không kiểm tra), nên gom về đây để cả ba dùng chung một luật.
 *
 * <p>Phải soi CẢ HAI bảng: {@code shipper_registers} giữ hồ sơ chờ/đã duyệt, còn
 * {@code shippers} giữ tài xế đang hoạt động — chỉ tra một bảng là lọt.
 */
@Component
@RequiredArgsConstructor
public class ShipperIdentityGuard {

    /** Không loại trừ ai (đăng ký mới, user chưa tồn tại) — dùng id không bao giờ có thật. */
    private static final Long NO_EXCLUSION = -1L;

    private final ShipperRepository shipperRepository;
    private final ShipperRegisterRepository shipperRegisterRepository;

    /**
     * @param rawIdCard      CCCD/CMND người dùng nhập (chưa chuẩn hoá), có thể null nếu không đổi
     * @param rawPlate       biển số người dùng nhập (chưa chuẩn hoá), có thể null nếu không đổi
     * @param excludeUserId  bỏ qua bản ghi của chính user này; null khi đăng ký mới
     */
    public void ensureUnique(String rawIdCard, String rawPlate, Long excludeUserId) {
        Long exclude = excludeUserId != null ? excludeUserId : NO_EXCLUSION;

        String idCard = ShipperIdentityNormalizer.normalizeIdCard(rawIdCard);
        if (idCard != null
                && (shipperRegisterRepository.existsByIdCardAndUser_UserIdNot(idCard, exclude)
                || shipperRepository.existsByIdCardAndUser_UserIdNot(idCard, exclude))) {
            throw new AppException(ErrorCode.ID_CARD_EXISTS);
        }

        String plateNorm = ShipperIdentityNormalizer.normalizeLicensePlate(rawPlate);
        if (plateNorm != null
                && (shipperRegisterRepository.existsByLicensePlateNormAndUser_UserIdNot(plateNorm, exclude)
                || shipperRepository.existsByLicensePlateNormAndUser_UserIdNot(plateNorm, exclude))) {
            throw new AppException(ErrorCode.LICENSE_PLATE_EXISTS);
        }
    }
}
