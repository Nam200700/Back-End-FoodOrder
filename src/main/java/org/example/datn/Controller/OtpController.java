package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.request.auth.SendOtpRequest;
import org.example.datn.DTO.request.auth.VerifyOtpRequest;
import org.example.datn.Service.OtpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> send(@Valid @RequestBody SendOtpRequest req) {
        otpService.sendOtp(req.getPhone(), req.getPurpose());
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã gửi mã OTP"));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verify(@Valid @RequestBody VerifyOtpRequest req) {
        boolean ok = otpService.verifyOtp(req.getPhone(), req.getCode(), req.getPurpose());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("verified", ok)));
    }
}
