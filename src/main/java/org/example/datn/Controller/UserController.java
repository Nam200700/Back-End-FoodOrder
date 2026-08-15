package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.DTO.response.voucher.VoucherResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.UserService;
import org.example.datn.Service.VoucherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.example.datn.DTO.request.auth.UpdateProfileRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final VoucherService voucherService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(user.getUserId())));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(user.getUserId(), req)));
    }

    @PostMapping("/owner/re-register")
    public ResponseEntity<ApiResponse<UserResponse>> ownerReRegister(
            @AuthenticationPrincipal CustomUserDetails user,
            @jakarta.validation.Valid @RequestBody org.example.datn.DTO.request.auth.OwnerReRegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.ownerReRegister(user.getUserId(), req)));
    }

    @PostMapping("/shipper/re-register")
    public ResponseEntity<ApiResponse<UserResponse>> shipperReRegister(
            @AuthenticationPrincipal CustomUserDetails user,
            @jakarta.validation.Valid @RequestBody org.example.datn.DTO.request.auth.ShipperReRegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.shipperReRegister(user.getUserId(), req)));
    }

    // ─── Loyalty: đổi điểm thưởng lấy voucher ───
    @GetMapping("/loyalty/catalog")
    public ResponseEntity<ApiResponse<List<VoucherResponse>>> loyaltyCatalog() {
        return ResponseEntity.ok(ApiResponse.ok(voucherService.getLoyaltyCatalog()));
    }

    @PostMapping("/loyalty/redeem")
    public ResponseEntity<ApiResponse<UserResponse>> redeemLoyalty(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, Long> body) {
        voucherService.redeemLoyalty(user.getUserId(), body.get("voucherId"));
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(user.getUserId())));
    }
}