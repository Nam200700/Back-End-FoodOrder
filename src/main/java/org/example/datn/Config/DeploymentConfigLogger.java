package org.example.datn.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In ra các thiết lập quyết định việc deploy có chạy được hay không, ngay khi app khởi động.
 *
 * <p>Lý do cần: mấy giá trị này sai thì triệu chứng rất dễ đánh lạc hướng. Thiếu tên miền
 * trong CORS thì các API đọc (GET) vẫn chạy ngon, chỉ thao tác ghi mới trả 403 — nhìn vào
 * dễ tưởng lỗi phân quyền. Quên bật {@code SPRING_PROFILES_ACTIVE=prod} thì app vẫn khởi
 * động bình thường nhưng lấy nguyên cấu hình dev (CORS trỏ localhost, cookie không Secure).
 *
 * <p>In thẳng ra log lúc khởi động thì chỉ cần liếc một dòng là biết cấu hình đã vào chưa,
 * thay vì phải dựng lại lỗi từ phía trình duyệt rồi lần ngược.
 */
@Slf4j
@Component
public class DeploymentConfigLogger {

    private final Environment environment;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    // Kiểu String chứ không phải boolean: giá trị mặc định là "auto" (suy từ request).
    @Value("${app.auth.cookie.secure}")
    private String cookieSecureMode;

    @Value("${app.auth.cookie.same-site}")
    private String cookieSameSite;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    public DeploymentConfigLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logEffectiveConfig() {
        String[] profiles = environment.getActiveProfiles();
        String profileList = profiles.length == 0 ? "(không có — đang dùng default)" : String.join(", ", profiles);

        String cookieSecureDesc = "auto".equalsIgnoreCase(cookieSecureMode)
                ? "auto (bật khi request đi qua HTTPS)"
                : cookieSecureMode + " (ép cứng)";

        log.info("──────── CẤU HÌNH TRIỂN KHAI ĐANG ÁP DỤNG ────────");
        log.info("  Profile         : {}", profileList);
        log.info("  CORS origins    : {}", allowedOrigins);
        log.info("  Cookie secure   : {}", cookieSecureDesc);
        log.info("  Cookie SameSite : {}", cookieSameSite);
        log.info("  Frontend URL    : {}", frontendBaseUrl);

        // Cảnh báo các tổ hợp chắc chắn hỏng khi chạy thật, để không phải chờ người dùng báo lỗi.
        boolean localhostOrigin = allowedOrigins.stream().anyMatch(o -> o.contains("localhost"));
        boolean httpsOrigin = allowedOrigins.stream().anyMatch(o -> o.startsWith("https://"));
        boolean forcedInsecure = "false".equalsIgnoreCase(cookieSecureMode);

        if (httpsOrigin && forcedInsecure) {
            log.warn("  [!] CORS cho phép origin HTTPS nhưng cookie secure bị ép false — cookie "
                    + "refresh token sẽ không được bảo vệ. Bỏ AUTH_COOKIE_SECURE để dùng auto.");
        }
        if ("None".equalsIgnoreCase(cookieSameSite) && forcedInsecure) {
            log.warn("  [!] SameSite=None bắt buộc phải đi kèm secure=true, nếu không trình duyệt "
                    + "sẽ từ chối lưu cookie.");
        }
        if (localhostOrigin && httpsOrigin) {
            log.warn("  [!] Danh sách CORS lẫn cả localhost và tên miền thật — nên bỏ localhost "
                    + "khỏi cấu hình chạy thật.");
        }
        if (frontendBaseUrl.contains("localhost") && httpsOrigin) {
            log.warn("  [!] Frontend URL còn trỏ localhost trong khi CORS đã là tên miền thật — "
                    + "link mời đơn nhóm sinh ra sẽ không mở được trên máy người khác.");
        }
        log.info("──────────────────────────────────────────────────");
    }
}
