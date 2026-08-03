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
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.domain.enums.VoucherStatus;
import org.example.datn.mapper.VoucherMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Voucher voucher = voucherMapper.toEntity(request);
        voucher.setUsedQuantity(0);
        voucher = voucherRepository.save(voucher);
        return voucherMapper.toResponse(voucher);
    }

    public VoucherResponse updateVoucher(Long voucherId, VoucherRequest request) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));
        if (!voucher.getCode().equals(request.getCode())
                && voucherRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTS);
        }
        voucherMapper.updateVoucher(voucher, request);
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


    //customer
    @Transactional(readOnly = true)
    public List<UserVoucherResponse> getMyVouchers(Long userId) {
        List<UserVoucher> list = userVoucherRepository.findByUser_UserIdAndUsed(userId, false);
        return list.stream().map(uv -> UserVoucherResponse.builder()
                .userVoucherId(uv.getUserVoucherId())
                .voucherId(uv.getVoucher().getVoucherId())
                .code(uv.getVoucher().getCode())
                .name(uv.getVoucher().getName())
                .discountType(uv.getVoucher().getDiscountType())
                .discountValue(uv.getVoucher().getDiscountValue())
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

        // Kiểm tra xem có phải voucher public không và còn hạn không
        if (voucher.getIssueType() != VoucherIssueType.EVENT || voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new AppException(ErrorCode.VOUCHER_NOT_FOUND);
        }

        if (LocalDateTime.now().isAfter(voucher.getEndDate())) {
            throw new AppException(ErrorCode.VOUCHER_EXPIRED);
        }

        // Kiểm tra xem user đã lưu mã này chưa
        boolean alreadyClaimed = userVoucherRepository.existsByUser_UserIdAndVoucher_VoucherId(userId, voucherId);
        if (alreadyClaimed) {
            throw new AppException(ErrorCode.VOUCHER_ALREADY_CLAIMED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Tạo bản ghi ví voucher cho user
        UserVoucher userVoucher = UserVoucher.builder()
                .user(user)
                .voucher(voucher)
                .receivedAt(LocalDateTime.now())
                .expiredAt(voucher.getEndDate())
                .used(false)
                .build();

        userVoucherRepository.save(userVoucher);
    }

    //hoàn lại voucher cho khách hàng khi đơn hàng bị hủy
    public void refundVoucher(Order order) {
        if (order.getUserVoucher() != null && order.getCustomer() != null) {
            UserVoucher userVoucher = order.getUserVoucher();

            userVoucherRepository.findById(userVoucher.getUserVoucherId())
                    .ifPresent(uv -> {
                        // Kiểm tra trạng thái đã sử dụng của UserVoucher
                        if (Boolean.TRUE.equals(uv.getUsed())) {
                            uv.setUsed(false);
                            uv.setUsedAt(null);
                            userVoucherRepository.save(uv);

                            // Giảm số lượng đã sử dụng (usedQuantity) của Voucher gốc
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