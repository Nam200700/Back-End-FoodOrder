package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Gửi OTP qua SMTP — chạy ASYNC trên pool "mailExecutor" (fire-and-forget).
     * Nhờ đó register/quên mật khẩu KHÔNG phải chờ SMTP và không giữ transaction DB mở
     * suốt lúc gửi. Lỗi gửi được nuốt + log fallback ngay trong hàm nên caller không cần chờ.
     */
    @Async("mailExecutor")
    public void sendOtp(String email, String code) {
        log.info("Sending real OTP email to {}...", email);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("[MealDash] Mã xác thực OTP của bạn");
            message.setText("Xin chào,\n\nMã OTP xác thực tài khoản của bạn tại MealDash là: " + code + "\n\nMã này có hiệu lực trong vòng 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\nTrân trọng,\nĐội ngũ MealDash");
            mailSender.send(message);
            log.info("Successfully sent OTP email to {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
            // Fallback log ra console để lấy OTP test tiếp nếu mail cấu hình sai hoặc chưa kích hoạt App Password
            log.info("[FALLBACK OTP] OTP for {} -> {}", email, code);
        }
    }
}
