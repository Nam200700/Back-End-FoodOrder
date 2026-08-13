package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.voucher.VoucherRequest;
import org.example.datn.DTO.response.voucher.UserVoucherResponse;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.DTO.response.voucher.VoucherStatsResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.UserVoucherRepository;
import org.example.datn.Repository.VoucherRepository;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Order;
import org.example.datn.domain.User;
import org.example.datn.domain.UserVoucher;
import org.example.datn.domain.Voucher;
import org.example.datn.domain.enums.DiscountType;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.domain.enums.VoucherStatus;
import org.example.datn.mapper.VoucherMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherMapper voucherMapper;
    private final UserVoucherRepository userVoucherRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public VoucherResponse getByIdVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        return voucherMapper.toResponse(voucher);
    }

    public VoucherResponse createVoucher(VoucherRequest request) {
        if (voucherRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTS);
        }
        if (request.getIssueType() == VoucherIssueType.ORDER_CANCELLED && request.getStatus() == VoucherStatus.ACTIVE) {
            boolean hasActiveCompensation = voucherRepository.existsActiveVoucherByIssueType(
                    VoucherIssueType.ORDER_CANCELLED, LocalDateTime.now()
            );
            if (hasActiveCompensation) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Đã có một Voucher đền bù hủy đơn đang hoạt động và chưa hết hạn. Vui lòng chờ voucher cũ hết hạn trước khi tạo mới!");
            }
        }

        // B. Nếu giảm cố định (FIXED) loại Sự kiện (EVENT) -> minOrderAmount BẮT BUỘC phải >= discountValue
        if (request.getDiscountType() == DiscountType.FIXED && request.getIssueType() == VoucherIssueType.EVENT) {
            if (request.getMinOrderAmount() == null || request.getMinOrderAmount().compareTo(request.getDiscountValue()) < 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Voucher giảm cố định loại Sự kiện bắt buộc có đơn tối thiểu lớn hơn hoặc bằng giá trị giảm!");
            }
        }

        Voucher voucher = voucherMapper.toEntity(request);
        voucher.setMinOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO);
        voucher.setUsedQuantity(0);

        voucher = voucherRepository.save(voucher);
        return voucherMapper.toResponse(voucher);
    }

    // 2. CẬP NHẬT VOUCHER (KHÓA TOÀN BỘ CÁC TRƯỜNG THUỘC TRỊ GIÁ)
    public VoucherResponse updateVoucher(Long voucherId, VoucherRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        if (!voucher.getCode().equalsIgnoreCase(request.getCode())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không được phép thay đổi Mã Voucher!");
        }
        if (voucher.getDiscountType() != request.getDiscountType() ||
                voucher.getDiscountValue().compareTo(request.getDiscountValue()) != 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không được phép thay đổi Loại hoặc Giá trị giảm của Voucher!");
        }
        if (voucher.getIssueType() != request.getIssueType()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không được phép thay đổi Loại phát hành Voucher!");
        }

        BigDecimal oldMin = voucher.getMinOrderAmount() != null ? voucher.getMinOrderAmount() : BigDecimal.ZERO;
        BigDecimal newMin = request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO;
        if (oldMin.compareTo(newMin) != 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không được phép thay đổi Giá trị đơn hàng tối thiểu!");
        }

        if (request.getIssueType() == VoucherIssueType.ORDER_CANCELLED && request.getStatus() == VoucherStatus.ACTIVE) {
            boolean hasActiveOther = voucherRepository.existsActiveVoucherByIssueTypeExcludingId(
                    VoucherIssueType.ORDER_CANCELLED, LocalDateTime.now(), voucherId
            );
            if (hasActiveOther) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Đã có một Voucher đền bù hủy đơn khác đang hoạt động và chưa hết hạn. Không thể kích hoạt!");
            }
        }

        voucher.setName(request.getName());
        voucher.setStartDate(request.getStartDate());
        voucher.setEndDate(request.getEndDate());
        voucher.setStatus(request.getStatus());
        voucher = voucherRepository.save(voucher);
        return voucherMapper.toResponse(voucher);
    }

    public VoucherResponse lockVoucher(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        voucher.setStatus(VoucherStatus.INACTIVE);
        voucher = voucherRepository.save(voucher);
        return voucherMapper.toResponse(voucher);
    }

    @Transactional(readOnly = true)
    public VoucherStatsResponse getVoucherStats() {
        return VoucherStatsResponse.builder()
                .totalVouchers(voucherRepository.count())
                .activeVouchers(voucherRepository.countByStatus(VoucherStatus.ACTIVE))
                .inactiveVouchers(voucherRepository.countByStatus(VoucherStatus.INACTIVE))
                .expiredVouchers(voucherRepository.countByEndDateBefore(LocalDateTime.now()))
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> getVouchersWithFilter(String keyword, VoucherStatus status, Pageable pageable) {
        Page<Voucher> page = voucherRepository.searchAndFilter(keyword, status, LocalDateTime.now(), pageable);
        return PageResponse.from(page.map(voucherMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public List<UserVoucherResponse> getMyVouchers(Long userId) {
        List<UserVoucher> list = userVoucherRepository.findValidUserVouchers(userId, false, LocalDateTime.now());
        return list.stream().map(uv -> UserVoucherResponse.builder()
                .userVoucherId(uv.getUserVoucherId())
                .voucherId(uv.getVoucher().getVoucherId())
                .code(uv.getVoucher().getCode())
                .name(uv.getVoucher().getName())
                .discountType(uv.getVoucher().getDiscountType())
                .discountValue(uv.getVoucher().getDiscountValue())
                .minOrderAmount(uv.getVoucher().getMinOrderAmount())
                .receivedAt(uv.getReceivedAt())
                .expiredAt(uv.getExpiredAt())
                .used(uv.getUsed())
                .usedAt(uv.getUsedAt())
                .build()
        ).toList();
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> getPublicVouchers(Long userId) {
        List<Voucher> vouchers = voucherRepository.findUnclaimedPublicVouchersForUser(
                VoucherIssueType.EVENT, VoucherStatus.ACTIVE, LocalDateTime.now(), userId
        );
        return vouchers.stream().map(voucherMapper::toResponse).toList();
    }

    public void claimPublicVoucher(Long userId, Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getIssueType() != VoucherIssueType.EVENT || voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        }

        if (LocalDateTime.now().isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }

        boolean alreadyClaimed = userVoucherRepository.existsByUser_UserIdAndVoucher_VoucherId(userId, voucherId);
        if (alreadyClaimed) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_CLAIMED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserVoucher userVoucher = UserVoucher.builder()
                .user(user)
                .voucher(voucher)
                .receivedAt(LocalDateTime.now())
                .expiredAt(voucher.getEndDate())
                .used(false)
                .build();

        userVoucherRepository.save(userVoucher);
    }

    // ─── LOYALTY: đổi điểm thưởng lấy voucher ───────────────────────────────

    /** Danh sách voucher LOYALTY đang mở để đổi bằng điểm (kèm pointsCost). */
    @Transactional(readOnly = true)
    public List<VoucherResponse> getLoyaltyCatalog() {
        return voucherRepository.findByIssueTypeAndStatusAndEndDateAfter(
                        VoucherIssueType.LOYALTY, VoucherStatus.ACTIVE, LocalDateTime.now())
                .stream()
                .map(v -> VoucherResponse.builder()
                        .voucherId(v.getVoucherId())
                        .code(v.getCode())
                        .name(v.getName())
                        .discountType(v.getDiscountType())
                        .discountValue(v.getDiscountValue())
                        .minOrderAmount(v.getMinOrderAmount())
                        .pointsCost(v.getPointsCost())
                        .startDate(v.getStartDate())
                        .endDate(v.getEndDate())
                        .status(v.getStatus())
                        .issueType(v.getIssueType())
                        .build())
                .toList();
    }

    /** Đổi điểm loyalty lấy 1 voucher LOYALTY: trừ điểm + cấp UserVoucher. */
    public void redeemLoyalty(Long userId, Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        if (voucher.getIssueType() != VoucherIssueType.LOYALTY || voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        }
        if (voucher.getEndDate() != null && LocalDateTime.now().isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }
        if (userVoucherRepository.existsByUser_UserIdAndVoucher_VoucherId(userId, voucherId)) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_CLAIMED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        int cost = voucher.getPointsCost() != null ? voucher.getPointsCost() : 0;
        int have = user.getLoyaltyPoints() != null ? user.getLoyaltyPoints() : 0;
        if (have < cost) {
            throw new AppException(ErrorCode.INSUFFICIENT_LOYALTY_POINTS);
        }

        user.setLoyaltyPoints(have - cost);
        userRepository.save(user);

        userVoucherRepository.save(UserVoucher.builder()
                .user(user)
                .voucher(voucher)
                .used(false)
                .receivedAt(LocalDateTime.now())
                .expiredAt(voucher.getEndDate())
                .build());
    }

    public void refundVoucher(Order order) {
        if (order.getUserVoucher() != null && order.getCustomer() != null) {
            UserVoucher userVoucher = order.getUserVoucher();

            userVoucherRepository.findById(userVoucher.getUserVoucherId())
                    .ifPresent(uv -> {
                        if (Boolean.TRUE.equals(uv.getUsed())) {
                            uv.setUsed(false);
                            uv.setUsedAt(null);
                            userVoucherRepository.save(uv);

                            Voucher voucher = uv.getVoucher();
                            if (voucher != null && voucher.getUsedQuantity() != null && voucher.getUsedQuantity() > 0) {
                                voucher.setUsedQuantity(voucher.getUsedQuantity() - 1);
                                voucherRepository.save(voucher);
                            }
                        }
                    });
        }
    }
}