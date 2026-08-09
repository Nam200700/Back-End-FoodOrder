package org.example.datn.Controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.request.auth.LoginRequest;
import org.example.datn.DTO.request.auth.RegisterRequest;
import org.example.datn.DTO.response.auth.AuthResponse;
import org.example.datn.DTO.response.auth.RefreshResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Service.AuthService;
import org.example.datn.Service.QrLoginService;
import org.example.datn.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final QrLoginService qrLoginService;

    /**
     * BUG-SEC-02 (nâng cấp): refresh token KHÔNG còn trả về trong JSON body cho JS đọc.
     * Nó được đặt vào cookie HttpOnly + Secure + SameSite -> JavaScript (kể cả mã XSS)
     * không thể đọc/đánh cắp. Access token (sống ngắn 15') vẫn trả trong body, FE chỉ
     * giữ trong bộ nhớ (không persist). Cookie chỉ gắn path /api/v1/auth nên chỉ theo
     * các request refresh/logout, thu hẹp tối đa bề mặt lộ.
     */
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    // Bật secure=true ở production (HTTPS). Dev localhost để false vì chạy http.
    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    // Lax hợp lệ cho FE/BE cùng site (localhost khác port vẫn same-site). Cross-domain
    // thật thì đặt None (bắt buộc kèm secure=true).
    @Value("${app.auth.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.jwt.refresh-token-expiry}")
    private long refreshTokenExpiry; // ms

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        // Đăng ký chưa cấp token (chờ xác thực OTP) -> không set cookie.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req,
                                                           HttpServletResponse response) {
        AuthResponse auth = authService.login(req);
        setRefreshCookie(response, auth.getRefreshToken());
        auth.setRefreshToken(null); // không lộ refresh token cho JS
        return ResponseEntity.ok(ApiResponse.ok(auth, "Đăng nhập thành công"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        // Đọc refresh token từ cookie HttpOnly (FE không gửi trong body nữa).
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AppException(ErrorCode.TOKEN_INVALID, "Không tìm thấy phiên đăng nhập. Vui lòng đăng nhập lại.");
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(refreshToken)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        // Xoá cookie refresh token phía trình duyệt (maxAge=0).
        clearRefreshCookie(response);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đăng xuất thành công"));
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
            @RequestBody java.util.Map<String, String> req,
            HttpServletResponse response) {
        String email = req.get("email");
        String code = req.get("otp");
        AuthResponse res = authService.verifyRegisterOtp(email, code);
        // Verify OTP thành công = cấp token -> đặt refresh cookie như login.
        setRefreshCookie(response, res.getRefreshToken());
        res.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.ok(res, "Xác thực OTP thành công!"));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<Void>> resendRegisterOtp(
            @RequestBody java.util.Map<String, String> req) {
        String email = req.get("email");
        authService.resendRegisterOtp(email);
        return ResponseEntity.ok(ApiResponse.ok(null, "Mã OTP mới đã được gửi qua email!"));
    }

    // ─── ĐĂNG NHẬP QR (kiểu Zalo/WhatsApp Web) ─────────────────────────────────
    // Tất cả nằm dưới /api/v1/auth/** (đã permitAll). approve/reject tự kiểm principal.

    /** Desktop tạo mã QR. Trả sessionId (nhúng vào QR) + pollSecret (bí mật, chỉ desktop giữ). */
    @PostMapping("/qr/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> qrCreate(
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        QrLoginService.CreateResult r = qrLoginService.create(userAgent);
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", r.getSessionId());
        body.put("pollSecret", r.getPollSecret());
        body.put("expiresIn", r.getExpiresIn());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    /** Desktop poll trạng thái (cần đúng pollSecret). */
    @GetMapping("/qr/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> qrStatus(
            @RequestParam String sid, @RequestParam String secret) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", qrLoginService.status(sid, secret));
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    /** Điện thoại mở trang duyệt → đánh dấu đã quét. */
    @PostMapping("/qr/scanned")
    public ResponseEntity<ApiResponse<Void>> qrScanned(@RequestBody Map<String, String> req) {
        qrLoginService.markScanned(req.get("sid"));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Người dùng (đã đăng nhập trên điện thoại) ĐỒNG Ý đăng nhập cho thiết bị kia. */
    @PostMapping("/qr/approve")
    public ResponseEntity<ApiResponse<Void>> qrApprove(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, String> req) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        qrLoginService.approve(req.get("sid"), user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xác nhận đăng nhập"));
    }

    /** Người dùng TỪ CHỐI. */
    @PostMapping("/qr/reject")
    public ResponseEntity<ApiResponse<Void>> qrReject(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody Map<String, String> req) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        qrLoginService.reject(req.get("sid"));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Desktop đổi lấy token sau khi được duyệt (một lần) — set refresh cookie như login. */
    @PostMapping("/qr/exchange")
    public ResponseEntity<ApiResponse<AuthResponse>> qrExchange(
            @RequestBody Map<String, String> req,
            HttpServletResponse response) {
        Long userId = qrLoginService.exchange(req.get("sid"), req.get("secret"));
        AuthResponse auth = authService.issueTokensForUserId(userId);
        setRefreshCookie(response, auth.getRefreshToken());
        auth.setRefreshToken(null);
        return ResponseEntity.ok(ApiResponse.ok(auth, "Đăng nhập thành công"));
    }

    // ─── Cookie helpers ────────────────────────────────────────────────────────
    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null) return;
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshTokenExpiry))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
