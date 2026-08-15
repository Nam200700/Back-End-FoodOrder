package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gửi SMS qua SpeedSMS (nhà cung cấp trong nước).
 *
 * <p>Dùng {@code sms_type = 4} — brandname dùng chung "Notify" của SpeedSMS, KHÔNG cần
 * đăng ký brandname riêng với nhà mạng (vốn đòi giấy phép kinh doanh + phí duy trì
 * hằng tháng). Tin tới máy khách hiện người gửi là "Notify".
 *
 * <p>Chỉ dùng cho luồng QUÊN MẬT KHẨU khi người dùng chọn nhận mã qua số điện thoại.
 * Đăng ký tài khoản vẫn xác thực bằng email (miễn phí) — xem AuthService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final String SEND_URL = "https://api.speedsms.vn/index.php/sms/send";

    private final RestTemplate restTemplate;

    @Value("${app.speedsms.access-token:}")
    private String accessToken;

    /**
     * Người gửi. Với sms_type = 2 thì ĐỂ TRỐNG — SpeedSMS tự dùng một số ngẫu nhiên.
     * Chỉ điền khi dùng sms_type 3/4 (tên brandname đã đăng ký) hoặc 5 (deviceId app Android).
     */
    @Value("${app.speedsms.sender:}")
    private String sender;

    /**
     * 2 = tin chăm sóc khách hàng, gửi từ số ngẫu nhiên — KHÔNG cần brandname, đây là
     *     đường duy nhất tài khoản mới gửi được ngay.
     * 3, 4 = cần brandname đã đăng ký với SpeedSMS (trang quảng cáo nói type 4 dùng
     *     "Notify" không cần đăng ký, nhưng thực tế API trả {@code sender not found}).
     * 5 = gửi qua app Android bằng SIM của chính mình, sender là deviceId.
     */
    @Value("${app.speedsms.sms-type:2}")
    private int smsType;

    public void sendOtp(String phone, String code) {
        if (accessToken == null || accessToken.isBlank()) {
            // Không cấu hình token thì báo lỗi rõ ràng thay vì im lặng "gửi thành công"
            // rồi để người dùng ngồi chờ một tin nhắn không bao giờ tới.
            log.error("Chưa cấu hình SPEEDSMS_ACCESS_TOKEN — không thể gửi OTP qua SMS.");
            throw new AppException(ErrorCode.SMS_SEND_FAILED);
        }

        // Viết KHÔNG DẤU: tin có dấu tiếng Việt chỉ được 70 ký tự/tin, không dấu được 160
        // -> nội dung dưới đây gói gọn trong 1 tin, tiết kiệm một nửa chi phí.
        String content = "Ma xac thuc cua ban la " + code
                + ". Ma co hieu luc 5 phut. Khong chia se ma nay cho bat ky ai.";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // SpeedSMS dùng HTTP Basic với access token làm username, password cố định là "x".
        headers.setBasicAuth(accessToken, "x", StandardCharsets.UTF_8);

        // Giữ đúng dạng request của client PHP chính thức: luôn có đủ 4 trường, "sender"
        // để chuỗi rỗng khi không dùng brandname. (Đã thử bỏ hẳn trường này — kết quả y hệt.)
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", List.of(phone));
        body.put("content", content);
        body.put("sms_type", smsType);
        body.put("sender", sender == null ? "" : sender);

        try {
            Map<?, ?> res = restTemplate.postForObject(
                    SEND_URL, new HttpEntity<>(body, headers), Map.class);

            // SpeedSMS luôn trả HTTP 200, thành công hay không nằm ở trường "status".
            if (res == null || !"success".equals(res.get("status"))) {
                log.error("SpeedSMS từ chối gửi OTP tới {}: {}", maskPhone(phone), res);
                throw new AppException(ErrorCode.SMS_SEND_FAILED);
            }
            // TUYỆT ĐỐI không log mã OTP: ai đọc được log là đăng nhập được tài khoản người khác.
            log.info("Đã gửi OTP qua SMS tới {}", maskPhone(phone));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi khi gọi SpeedSMS gửi OTP tới {}", maskPhone(phone), e);
            throw new AppException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    /** 0912345678 -> 0912***678, đủ để tra log mà không lộ trọn số thuê bao. */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
    }
}
