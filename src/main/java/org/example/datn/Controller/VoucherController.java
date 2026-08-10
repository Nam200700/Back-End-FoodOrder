package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.request.voucher.VoucherRequest;
import org.example.datn.DTO.response.voucher.UserVoucherResponse;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.DTO.response.voucher.VoucherStatsResponse;
import org.example.datn.Service.VoucherService;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.enums.VoucherStatus;
import org.example.datn.security.CustomUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> getVouchers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VoucherStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getVouchersWithFilter(keyword, status, pageable)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getVoucherStats()));
    }

    @GetMapping("/{voucherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherResponse>> getByIdVoucher(
            @PathVariable Long voucherId) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getByIdVoucher(voucherId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherResponse>> createVoucher(
            @Valid @RequestBody VoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.createVoucher(request)));
    }

    @PutMapping("/{voucherId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateVoucher(
            @PathVariable Long voucherId,
            @Valid @RequestBody VoucherRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(voucherService.updateVoucher(voucherId, request)));
    }

    @PutMapping("/{voucherId}/inactive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VoucherResponse>> lockVoucher(
            @PathVariable Long voucherId) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.lockVoucher(voucherId)));
    }

    @GetMapping("/my-vouchers")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<UserVoucherResponse>>> getMyVouchers(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getMyVouchers(user.getUserId())));
    }

    @GetMapping("/public")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> getPublicVouchers(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getPublicVouchers(user.getUserId())));
    }

    @PostMapping("/{voucherId}/add")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Void>> claimPublicVoucher(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long voucherId) {
        voucherService.claimPublicVoucher(user.getUserId(), voucherId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}