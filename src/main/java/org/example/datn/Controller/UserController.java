package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.example.datn.DTO.request.auth.UpdateProfileRequest;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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

    @org.springframework.web.bind.annotation.PostMapping("/owner/re-register")
    public ResponseEntity<ApiResponse<UserResponse>> ownerReRegister(
            @AuthenticationPrincipal CustomUserDetails user,
            @jakarta.validation.Valid @RequestBody org.example.datn.DTO.request.auth.OwnerReRegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.ownerReRegister(user.getUserId(), req)));
    }

    @org.springframework.web.bind.annotation.PostMapping("/shipper/re-register")
    public ResponseEntity<ApiResponse<UserResponse>> shipperReRegister(
            @AuthenticationPrincipal CustomUserDetails user,
            @jakarta.validation.Valid @RequestBody org.example.datn.DTO.request.auth.ShipperReRegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.shipperReRegister(user.getUserId(), req)));
    }
}