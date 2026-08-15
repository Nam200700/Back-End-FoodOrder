package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.User;
import org.example.datn.domain.RestaurantRegister;
import org.example.datn.domain.ShipperRegister;
import org.example.datn.domain.enums.Role;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.domain.enums.VehicleType;
import org.example.datn.DTO.request.auth.LoginRequest;
import org.example.datn.DTO.request.auth.RefreshRequest;
import org.example.datn.DTO.request.auth.RegisterRequest;
import org.example.datn.DTO.request.auth.ForgotPasswordSendOtpRequest;
import org.example.datn.DTO.request.auth.ForgotPasswordResetRequest;
import org.example.datn.DTO.response.auth.AuthResponse;
import org.example.datn.DTO.response.auth.RefreshResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.EmailNotVerifiedException;
import org.example.datn.mapper.UserMapper;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.RestaurantRegisterRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.ShipperRegisterRepository;
import org.example.datn.Repository.OtpRepository;
import org.example.datn.domain.Otp;
import org.example.datn.domain.enums.OtpPurpose;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import org.example.datn.Service.SmsService;
import org.example.datn.Service.EmailService;
import org.example.datn.security.JwtTokenProvider;
import org.example.datn.util.DateTimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final RestaurantRegisterRepository restaurantRegisterRepository;
    private final RestaurantRepository restaurantRepository;
    private final ShipperRegisterRepository shipperRegisterRepository;
    private final OtpRepository otpRepository;
    private final SmsService smsService;
    private final EmailService emailService;

    @Value("${app.otp.max-fail-attempts}")
    private int maxFailAttempts;

    @Value("${app.otp.lockout-minutes}")
    private int lockoutMinutes;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByPhone(req.getPhone())) {
            throw new AppException(ErrorCode.PHONE_EXISTS);
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()
                && userRepository.existsByEmail(req.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTS);
        }
        // Check trùng CCCD/CMND và biển số xe (chỉ áp dụng khi đăng ký SHIPPER)
        if ("SHIPPER".equalsIgnoreCase(req.getRole())) {
            if (req.getIdCard() != null && !req.getIdCard().isBlank()
                    && shipperRegisterRepository.existsByIdCard(req.getIdCard())) {
                throw new AppException(ErrorCode.ID_CARD_EXISTS);
            }
            if (req.getLicensePlate() != null && !req.getLicensePlate().isBlank()
                    && shipperRegisterRepository.existsByLicensePlate(req.getLicensePlate())) {
                throw new AppException(ErrorCode.LICENSE_PLATE_EXISTS);
            }
        }
        // Check trùng SĐT quán (chỉ khi đăng ký OWNER và có nhập SĐT quán) — cả hồ sơ chờ duyệt lẫn quán đã duyệt
        if ("OWNER".equalsIgnoreCase(req.getRole())
                && req.getRestaurantPhone() != null && !req.getRestaurantPhone().isBlank()
                && (restaurantRegisterRepository.existsByPhone(req.getRestaurantPhone())
                    || restaurantRepository.existsByPhone(req.getRestaurantPhone()))) {
            throw new AppException(ErrorCode.RESTAURANT_PHONE_EXISTS);
        }


        org.example.datn.domain.enums.Role userRole = org.example.datn.domain.enums.Role.CUSTOMER;
        if (req.getRole() != null && !req.getRole().isBlank()) {
            try {
                userRole = org.example.datn.domain.enums.Role.valueOf(req.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Keep default
            }
        }

        // Đăng ký mới luôn bắt đầu bằng status set false cho đến khi verify OTP thành công.
        // emailVerified set false: chưa xác thực OTP khi login() sẽ chặn cho tới khi verify.
        User user = User.builder()
                .fullName(req.getFullName())
                .phone(req.getPhone())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(userRole)
                .status(false)
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        // Lưu thông tin đăng ký đối tác
        if (userRole == org.example.datn.domain.enums.Role.OWNER) {
            RestaurantRegister reg = RestaurantRegister.builder()
                    .owner(user)
                    .restaurantName(req.getRestaurantName() != null && !req.getRestaurantName().isBlank() ? req.getRestaurantName() : "Quán ăn đối tác mới")
                    .address(req.getRestaurantAddress() != null && !req.getRestaurantAddress().isBlank() ? req.getRestaurantAddress() : "Chưa cung cấp")
                    .latitude(req.getRestaurantLatitude())
                    .longitude(req.getRestaurantLongitude())
                    .phone(req.getRestaurantPhone() != null && !req.getRestaurantPhone().isBlank() ? req.getRestaurantPhone() : req.getPhone())
                    .description(req.getRestaurantDescription())
                    .imageUrl(req.getRestaurantImageUrl())
                    .status(RegisterStatus.PENDING)
                    .build();
            restaurantRegisterRepository.save(reg);
        } else if (userRole == org.example.datn.domain.enums.Role.SHIPPER) {
            VehicleType vehicleType = VehicleType.MOTORBIKE;
            if (req.getVehicleType() != null && !req.getVehicleType().isBlank()) {
                try {
                    vehicleType = VehicleType.valueOf(req.getVehicleType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    // default MOTORBIKE
                }
            }
            ShipperRegister reg = ShipperRegister.builder()
                    .user(user)
                    .idCard(req.getIdCard() != null && !req.getIdCard().isBlank() ? req.getIdCard() : "Chưa cung cấp")
                    .vehicleType(vehicleType)
                    .licensePlate(req.getLicensePlate() != null && !req.getLicensePlate().isBlank() ? req.getLicensePlate() : "Chưa cung cấp")
                    .status(RegisterStatus.PENDING)
                    .build();
            shipperRegisterRepository.save(reg);
        }

        // Tạo & lưu OTP REGISTER mới rồi gửi qua email
        String code = generateAndStoreOtp(user.getEmail(), OtpPurpose.REGISTER);
        emailService.sendOtp(user.getEmail(), code);

        return AuthResponse.builder()
                .token(null)
                .refreshToken(null)
                .user(userMapper.toResponse(user))
                .build();
    }

    /*
     * noRollbackFor = AppException: khi nhập sai mã, ta TĂNG fail_count rồi mới ném lỗi.
     * AppException kế thừa RuntimeException nên mặc định Spring sẽ rollback -> lệnh tăng
     * fail_count bị cuộn ngược, bộ đếm mãi bằng 0 và KHÔNG BAO GIỜ khóa được tài khoản.
     * Ở method này mọi thao tác ghi khác đều nằm ở nhánh thành công (sau khi mã đã đúng),
     * nên cho commit khi ném AppException là an toàn: chỉ đúng fail_count được lưu lại.
     */
    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse verifyRegisterOtp(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này!"));

        org.example.datn.domain.Otp otp = otpRepository.findFirstByRecipientAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(email, org.example.datn.domain.enums.OtpPurpose.REGISTER)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID, "Mã OTP không hợp lệ hoặc đã hết hạn!"));

        if (otp.getFailCount() >= maxFailAttempts) {
            java.time.LocalDateTime lockUntil = otp.getCreatedAt().plusMinutes(lockoutMinutes);
            if (java.time.LocalDateTime.now().isBefore(lockUntil)) {
                throw new AppException(ErrorCode.OTP_LOCKED, "Tài khoản tạm khóa đến " + DateTimeUtils.formatVi(lockUntil));
            }
        }

        if (otp.getExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED, "Mã OTP đã hết hạn!");
        }

        if (!otp.getCode().equals(code)) {
            otp.setFailCount(otp.getFailCount() + 1);
            otpRepository.save(otp);
            if (otp.getFailCount() >= maxFailAttempts) {
                java.time.LocalDateTime lockUntil = otp.getCreatedAt().plusMinutes(lockoutMinutes);
                throw new AppException(ErrorCode.OTP_LOCKED, "Tài khoản tạm khóa đến " + DateTimeUtils.formatVi(lockUntil));
            }
            throw new AppException(ErrorCode.OTP_WRONG_CODE, "Mã OTP không chính xác!");
        }

        // Đánh dấu OTP đã dùng
        otp.setIsUsed(true);
        otpRepository.save(otp);

        // Verify OTP thành công = đã xác thực email cho MỌI role -> cho phép login
        user.setEmailVerified(true);

        // Kích hoạt tài khoản
        if (user.getRole() == org.example.datn.domain.enums.Role.CUSTOMER) {
            user.setStatus(true); // CUSTOMER được dùng ngay
        } else {
            user.setStatus(false); // OWNER/SHIPPER vẫn ở status false chờ Admin phê duyệt
        }
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public void resendRegisterOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với email này!"));

        // Kiểm tra lockout OTP gần nhất trước khi gửi lại
        otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, org.example.datn.domain.enums.OtpPurpose.REGISTER).ifPresent(latest -> {
            if (latest.getFailCount() >= maxFailAttempts && latest.getCreatedAt() != null) {
                java.time.LocalDateTime lockUntil = latest.getCreatedAt().plusMinutes(lockoutMinutes);
                if (java.time.LocalDateTime.now().isBefore(lockUntil)) {
                    throw new AppException(ErrorCode.OTP_LOCKED, "Tài khoản tạm khóa đến " + DateTimeUtils.formatVi(lockUntil));
                }
            }
            // Chống spam OTP (gửi lại cách nhau tối thiểu 60s)
            if (latest.getCreatedAt() != null && latest.getCreatedAt().plusSeconds(60).isAfter(java.time.LocalDateTime.now())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Vui lòng đợi 60 giây trước khi yêu cầu mã OTP mới!");
            }
        });

        // Tạo & lưu OTP REGISTER mới rồi gửi qua email
        String code = generateAndStoreOtp(email, OtpPurpose.REGISTER);
        emailService.sendOtp(email, code);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByPhone(req.getPhone())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS));

        if (user.getPassword() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getLockedAt() != null) {
            String reason = user.getLockedReason() != null ? user.getLockedReason() : "Không có lý do cụ thể";
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị khóa. Lý do: " + reason);
        }

        // Chặn login khi chưa xác thực OTP/email. Đặt SAU khi mật khẩu đã đúng nên chỉ
        // chính chủ mới nhận được mã lỗi kèm email -> FE tự chuyển sang trang OTP; người
        // lạ dò tài khoản (không có mật khẩu) vẫn chỉ nhận BAD_CREDENTIALS chung chung.
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotVerifiedException(user.getEmail());
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(RefreshRequest req) {
        // Overload cũ (đọc từ body) — giữ lại để tương thích ngược, uỷ quyền cho bản String.
        return refresh(req.getRefreshToken());
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(String token) {
        // Refresh token nay lấy từ cookie HttpOnly (xem AuthController) thay vì body.
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isRefreshToken(token)) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        // Không cấp access token mới nếu tài khoản đã bị khoá sau khi đăng nhập
        // (nếu không, user bị khoá vẫn tự làm mới token tới 7 ngày).
        if (user.getLockedAt() != null) {
            String reason = user.getLockedReason() != null ? user.getLockedReason() : "Không có lý do cụ thể";
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị khóa. Lý do: " + reason);
        }
        return new RefreshResponse(jwtTokenProvider.generateAccessToken(user));
    }

    public AuthResponse buildAuthResponse(User user) {
        return AuthResponse.builder()
                .token(jwtTokenProvider.generateAccessToken(user))
                .refreshToken(jwtTokenProvider.generateRefreshToken(user))
                .user(userMapper.toResponse(user))
                .build();
    }

    /**
     * Cấp token cho userId đã được xác nhận (dùng cho luồng đăng nhập QR sau khi
     * người dùng đã duyệt trên điện thoại). Kiểm tra khoá tài khoản như các luồng khác.
     */
    @Transactional(readOnly = true)
    public AuthResponse issueTokensForUserId(Long userId) {
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        if (user.getLockedAt() != null) {
            String reason = user.getLockedReason() != null ? user.getLockedReason() : "Không có lý do cụ thể";
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản của bạn đã bị khóa. Lý do: " + reason);
        }
        return buildAuthResponse(user);
    }

    /**
     * Vô hiệu hoá OTP cũ, tạo mã 6 số mới (TTL 5 phút) và lưu. Trả về mã để caller tự gửi (email/SMS).
     * Gom logic trùng lặp ở register / resendRegisterOtp / forgotPasswordSendOtp.
     */
    private String generateAndStoreOtp(String target, OtpPurpose purpose) {
        otpRepository.invalidateOldOtps(target, purpose); // target = nơi nhận (email/SĐT)
        SecureRandom random = new SecureRandom();
        StringBuilder codeBuilder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            codeBuilder.append(random.nextInt(10));
        }
        String code = codeBuilder.toString();
        otpRepository.save(Otp.builder()
                .recipient(target)
                .code(code)
                .purpose(purpose)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .failCount(0)
                .isUsed(false)
                .build());
        return code;
    }

    @Transactional
    public void forgotPasswordSendOtp(ForgotPasswordSendOtpRequest req) {
        String phoneOrEmail = req.getPhoneOrEmail();
        String method = req.getMethod();

        // Kiểm tra xem User có tồn tại không
        userRepository.findByPhone(phoneOrEmail)
                .orElseGet(() -> userRepository.findByEmail(phoneOrEmail)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với thông tin đã cung cấp!")));

        // Kiểm tra lockout OTP gần nhất trước khi gửi lại
        otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(phoneOrEmail, org.example.datn.domain.enums.OtpPurpose.RESET_PASSWORD).ifPresent(latest -> {
            if (latest.getFailCount() >= maxFailAttempts && latest.getCreatedAt() != null) {
                java.time.LocalDateTime lockUntil = latest.getCreatedAt().plusMinutes(lockoutMinutes);
                if (java.time.LocalDateTime.now().isBefore(lockUntil)) {
                    throw new AppException(ErrorCode.OTP_LOCKED, "Tài khoản tạm khóa đến " + DateTimeUtils.formatVi(lockUntil));
                }
            }
            // Chống spam OTP (gửi lại cách nhau tối thiểu 60s)
            if (latest.getCreatedAt() != null && latest.getCreatedAt().plusSeconds(60).isAfter(java.time.LocalDateTime.now())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Vui lòng đợi 60 giây trước khi yêu cầu mã OTP mới!");
            }
        });

        // Tạo & lưu OTP RESET_PASSWORD mới
        String code = generateAndStoreOtp(phoneOrEmail, OtpPurpose.RESET_PASSWORD);

        // Gửi OTP theo phương thức yêu cầu
        if ("EMAIL".equalsIgnoreCase(method)) {
            emailService.sendOtp(phoneOrEmail, code);
        } else {
            smsService.sendOtp(phoneOrEmail, code);
        }
    }

    // noRollbackFor: giữ lại fail_count khi nhập sai OTP — xem giải thích ở verifyRegisterOtp.
    @Transactional(noRollbackFor = AppException.class)
    public void forgotPasswordReset(ForgotPasswordResetRequest req) {
        String phoneOrEmail = req.getPhoneOrEmail();
        String otpCode = req.getOtpCode();
        String newPassword = req.getNewPassword();

        // Kiểm tra OTP
        org.example.datn.domain.Otp otp = otpRepository.findFirstByRecipientAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(phoneOrEmail, org.example.datn.domain.enums.OtpPurpose.RESET_PASSWORD)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID, "Mã OTP không hợp lệ hoặc đã được sử dụng!"));

        if (otp.getFailCount() >= maxFailAttempts) {
            java.time.LocalDateTime lockUntil = otp.getCreatedAt().plusMinutes(lockoutMinutes);
            if (java.time.LocalDateTime.now().isBefore(lockUntil)) {
                throw new AppException(ErrorCode.OTP_LOCKED, "Tài khoản tạm khóa đến " + DateTimeUtils.formatVi(lockUntil));
            }
        }

        if (otp.getExpiredAt().isBefore(java.time.LocalDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED, "Mã OTP đã hết hạn!");
        }

        if (!otp.getCode().equals(otpCode)) {
            otp.setFailCount(otp.getFailCount() + 1);
            otpRepository.save(otp);
            if (otp.getFailCount() >= maxFailAttempts) {
                java.time.LocalDateTime lockUntil = otp.getCreatedAt().plusMinutes(lockoutMinutes);
                throw new AppException(ErrorCode.OTP_LOCKED, "Tài khoản tạm khóa đến " + DateTimeUtils.formatVi(lockUntil));
            }
            throw new AppException(ErrorCode.OTP_WRONG_CODE, "Mã OTP nhập vào không chính xác!");
        }

        // Đánh dấu OTP đã dùng
        otp.setIsUsed(true);
        otpRepository.save(otp);

        // Tìm User để cập nhật mật khẩu mới
        User user = userRepository.findByPhone(phoneOrEmail)
                .orElseGet(() -> userRepository.findByEmail(phoneOrEmail)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản với thông tin đã cung cấp!")));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
