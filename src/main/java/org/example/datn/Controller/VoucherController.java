package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.voucher.VoucherRequest;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.DTO.response.voucher.VoucherStatsResponse;
import org.example.datn.Service.VoucherService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.VoucherStatus;
import org.springframework.data.domain.Pageable;
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

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> getVouchers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VoucherStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getVouchersWithFilter(keyword, status, pageable)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<VoucherStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getVoucherStats()));
    }

    @GetMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherResponse>> getByIdVoucher(
            @PathVariable Long voucherId) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getByIdVoucher(voucherId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VoucherResponse>> createVoucher(
            @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.createVoucher(request)));
    }

    @PutMapping("/{voucherId}")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateVoucher(
            @PathVariable Long voucherId,
            @Valid @RequestBody VoucherRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(voucherService.updateVoucher(voucherId, request)));
    }

    @PutMapping("/{voucherId}/inactive")
    public ResponseEntity<ApiResponse<VoucherResponse>> lockVoucher(
            @PathVariable Long voucherId) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.lockVoucher(voucherId)));
    }
}