package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.voucher.VoucherRequest;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.Service.VoucherService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.VoucherStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class VoucherController {

    private final VoucherService voucherService;

    /**
     * Lấy danh sách voucher
     */
    /**
     * Lấy danh sách voucher có phân trang, tìm kiếm và lọc theo trạng thái
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> getVouchers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VoucherStatus status,
            org.springframework.data.domain.Pageable pageable) {

        return ResponseEntity.ok(
                ApiResponse.ok(voucherService.getVouchersWithFilter(keyword, status, pageable))
        );
    }

    /**
     * Lấy thống kê KPI voucher
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<org.example.datn.DTO.response.voucher.VoucherStatsResponse>> getStats() {
        return ResponseEntity.ok(
                ApiResponse.ok(voucherService.getVoucherStats())
        );
    }

    /**
     * Lấy chi tiết voucher
     */
    @GetMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getById(
            @PathVariable Long voucherId) {

        return ResponseEntity.ok(
                ApiResponse.ok(voucherService.getById(voucherId))
        );
    }

    /**
     * Thêm voucher
     */
    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> create(
            @Valid @RequestBody VoucherRequest request) {

        return ResponseEntity.ok(
                ApiResponse.ok(voucherService.create(request))
        );
    }

    /**
     * Cập nhật voucher
     */
    @PutMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable Long voucherId,
            @Valid @RequestBody VoucherRequest request) {

        return ResponseEntity.ok(
                ApiResponse.ok(voucherService.update(voucherId, request))
        );
    }

    /**
     * Xóa voucher
     */
    @DeleteMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long voucherId) {

        voucherService.delete(voucherId);

        return ResponseEntity.ok(
                ApiResponse.ok(null)
        );
    }
}