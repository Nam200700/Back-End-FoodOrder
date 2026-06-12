package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.request.auth.LoginRequest;
import org.example.datn.DTO.request.auth.RefreshRequest;
import org.example.datn.DTO.request.auth.RegisterRequest;
import org.example.datn.DTO.response.auth.AuthResponse;
import org.example.datn.DTO.response.auth.RefreshResponse;
import org.example.datn.Service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req), "Đăng nhập thành công"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(req)));
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<ApiResponse<Void>> forgotPasswordSendOtp(
            @Valid @RequestBody org.example.datn.DTO.request.auth.ForgotPasswordSendOtpRequest req) {
        authService.forgotPasswordSendOtp(req);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mã OTP khôi phục mật khẩu đã được gửi!"));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> forgotPasswordReset(
            @Valid @RequestBody org.example.datn.DTO.request.auth.ForgotPasswordResetRequest req) {
        authService.forgotPasswordReset(req);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đặt lại mật khẩu thành công! Hãy đăng nhập bằng mật khẩu mới."));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegisterOtp(
            @RequestBody java.util.Map<String, String> req) {
        String email = req.get("email");
        String code = req.get("otp");
        AuthResponse res = authService.verifyRegisterOtp(email, code);
        return ResponseEntity.ok(ApiResponse.ok(res, "Xác thực OTP thành công!"));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendRegisterOtp(
            @RequestBody java.util.Map<String, String> req) {
        String email = req.get("email");
        authService.resendRegisterOtp(email);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mã OTP mới đã được gửi qua email!"));
    }
}
