package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.voucher.VoucherRequest;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.DTO.response.voucher.VoucherStatsResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.VoucherRepository;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Voucher;
import org.example.datn.domain.enums.VoucherStatus;
import org.example.datn.mapper.VoucherMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherMapper voucherMapper;

    /**
     * Lấy danh sách voucher
     */
    @Transactional(readOnly = true)
    public List<VoucherResponse> getAll() {
        return voucherRepository.findAll()
                .stream()
                .map(voucherMapper::toResponse)
                .toList();
    }

    /**
     * Lấy chi tiết voucher
     */
    @Transactional(readOnly = true)
    public VoucherResponse getById(Long voucherId) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        return voucherMapper.toResponse(voucher);
    }

    /**
     * Thêm voucher
     */
    public VoucherResponse create(VoucherRequest request) {

        if (voucherRepository.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.VOUCHER_CODE_EXISTS);
        }

        Voucher voucher = voucherMapper.toEntity(request);

        voucher.setUsedQuantity(0);

        voucher = voucherRepository.save(voucher);

        return voucherMapper.toResponse(voucher);
    }

    /**
     * Cập nhật voucher
     */
    public VoucherResponse update(Long voucherId, VoucherRequest request) {

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

    /**
     * Xóa voucher
     */
    public void delete(Long voucherId) {

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_FOUND));

        voucherRepository.delete(voucher);
    }

    /**
     * Lấy thống kê KPI voucher
     */
    @Transactional(readOnly = true)
    public VoucherStatsResponse getVoucherStats() {
        return VoucherStatsResponse.builder()
                .totalVouchers(voucherRepository.count())
                .activeVouchers(voucherRepository.countByStatus(org.example.datn.domain.enums.VoucherStatus.ACTIVE))
                .inactiveVouchers(voucherRepository.countByStatus(org.example.datn.domain.enums.VoucherStatus.INACTIVE))
                .build();
    }

    /**
     * Lấy danh sách voucher có phân trang, tìm kiếm và lọc trạng thái
     */
    @Transactional(readOnly = true)
    public PageResponse<VoucherResponse> getVouchersWithFilter(String keyword, VoucherStatus status, Pageable pageable) {
        org.springframework.data.domain.Page<Voucher> page = voucherRepository.searchAndFilter(keyword, status, pageable);
        return PageResponse.from(page.map(voucherMapper::toResponse));
    }
}