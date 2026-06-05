package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Otp;
import org.example.datn.domain.enums.OtpPurpose;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.OtpRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final SmsService smsService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.length}")
    private int otpLength;
    @Value("${app.otp.ttl-minutes}")
    private int ttlMinutes;
    @Value("${app.otp.max-fail-attempts}")
    private int maxFailAttempts;
    @Value("${app.otp.lockout-minutes}")
    private int lockoutMinutes;

    @Transactional
    public void sendOtp(String phone, OtpPurpose purpose) {
        otpRepository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose).ifPresent(latest -> {
            if (latest.getFailCount() >= maxFailAttempts && latest.getCreatedAt() != null) {
                LocalDateTime lockUntil = latest.getCreatedAt().plusMinutes(lockoutMinutes);
                if (LocalDateTime.now().isBefore(lockUntil)) {
                    throw new AppException(ErrorCode.OTP_LOCKED,
                            "Tài khoản tạm khóa đến " + lockUntil);
                }
            }
        });

        otpRepository.invalidateOldOtps(phone, purpose);

        String code = generateCode();
        otpRepository.save(Otp.builder()
                .phone(phone)
                .code(code)
                .purpose(purpose)
                .expiredAt(LocalDateTime.now().plusMinutes(ttlMinutes))
                .failCount(0)
                .isUsed(false)
                .build());

        smsService.sendOtp(phone, code);
    }

    @Transactional
    public boolean verifyOtp(String phone, String code, OtpPurpose purpose) {
        Otp otp = otpRepository.findFirstByPhoneAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(phone, purpose)
                .orElseThrow(() -> new AppException(ErrorCode.OTP_INVALID));

        if (otp.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }
        if (!otp.getCode().equals(code)) {
            otp.setFailCount(otp.getFailCount() + 1);
            otpRepository.save(otp);
            throw new AppException(ErrorCode.OTP_WRONG_CODE);
        }
        otp.setIsUsed(true);
        otpRepository.save(otp);
        return true;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
