package org.example.datn.Service;

import lombok.Getter;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý phiên ĐĂNG NHẬP QR (kiểu Zalo/WhatsApp Web) hoàn toàn IN-MEMORY — không đụng DB,
 * không cần Flyway. Phiên sống ngắn (2 phút), dùng một lần.
 *
 * Bảo mật: QR chỉ chứa {@code sessionId} (công khai). {@code pollSecret} chỉ trả cho máy tạo mã
 * (desktop) và KHÔNG nằm trong QR → kẻ chụp trộm QR không thể poll/đổi lấy token.
 */
@Service
public class QrLoginService {

    /** Thời gian sống của mã QR (giây). */
    private static final long TTL_SECONDS = 120;
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Status { PENDING, SCANNED, APPROVED, REJECTED, CONSUMED }

    private final ConcurrentHashMap<String, QrSession> sessions = new ConcurrentHashMap<>();

    /** Kết quả tạo phiên trả cho desktop. */
    @Getter
    public static class CreateResult {
        private final String sessionId;
        private final String pollSecret;
        private final long expiresIn; // giây
        public CreateResult(String sessionId, String pollSecret, long expiresIn) {
            this.sessionId = sessionId;
            this.pollSecret = pollSecret;
            this.expiresIn = expiresIn;
        }
    }

    private static class QrSession {
        final String pollSecret;
        final Instant expiresAt;
        final String deviceInfo;
        volatile Status status;
        volatile Long userId;
        QrSession(String pollSecret, Instant expiresAt, String deviceInfo) {
            this.pollSecret = pollSecret;
            this.expiresAt = expiresAt;
            this.deviceInfo = deviceInfo;
            this.status = Status.PENDING;
        }
        boolean expired() { return Instant.now().isAfter(expiresAt); }
    }

    /** Desktop tạo mã. */
    public CreateResult create(String deviceInfo) {
        sweepExpired();
        String sessionId = UUID.randomUUID().toString();
        String pollSecret = randomToken();
        Instant exp = Instant.now().plusSeconds(TTL_SECONDS);
        sessions.put(sessionId, new QrSession(pollSecret, exp, deviceInfo));
        return new CreateResult(sessionId, pollSecret, TTL_SECONDS);
    }

    /** Điện thoại mở trang duyệt → đánh dấu ĐÃ QUÉT (chỉ khi đang chờ). */
    public void markScanned(String sessionId) {
        QrSession s = require(sessionId);
        if (s.status == Status.PENDING) s.status = Status.SCANNED;
    }

    /** Người dùng (đã đăng nhập) ĐỒNG Ý → gán userId + APPROVED. */
    public void approve(String sessionId, Long userId) {
        QrSession s = require(sessionId);
        if (s.status != Status.PENDING && s.status != Status.SCANNED) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID);
        }
        s.userId = userId;
        s.status = Status.APPROVED;
    }

    /** Người dùng TỪ CHỐI. */
    public void reject(String sessionId) {
        QrSession s = require(sessionId);
        if (s.status == Status.PENDING || s.status == Status.SCANNED) s.status = Status.REJECTED;
    }

    /** Desktop poll trạng thái (phải đúng pollSecret). Trả "EXPIRED" nếu hết hạn/không còn. */
    public String status(String sessionId, String secret) {
        QrSession s = sessions.get(sessionId);
        if (s == null) return "EXPIRED"; // đã dọn hoặc không tồn tại → coi như hết hạn
        checkSecret(s, secret);
        if (s.expired()) return "EXPIRED";
        return s.status.name();
    }

    /** Desktop đổi lấy userId sau khi APPROVED — DÙNG MỘT LẦN. */
    public Long exchange(String sessionId, String secret) {
        QrSession s = require(sessionId);
        checkSecret(s, secret);
        if (s.status != Status.APPROVED || s.userId == null) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID);
        }
        sessions.remove(sessionId); // tiêu thụ một lần
        return s.userId;
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private QrSession require(String sessionId) {
        QrSession s = sessionId == null ? null : sessions.get(sessionId);
        if (s == null) throw new AppException(ErrorCode.QR_SESSION_INVALID);
        if (s.expired()) {
            sessions.remove(sessionId);
            throw new AppException(ErrorCode.QR_SESSION_EXPIRED);
        }
        return s;
    }

    private void checkSecret(QrSession s, String secret) {
        if (secret == null || !constantEquals(s.pollSecret, secret)) {
            throw new AppException(ErrorCode.QR_SESSION_INVALID);
        }
    }

    private static boolean constantEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    private static String randomToken() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private void sweepExpired() {
        Instant now = Instant.now();
        sessions.values().removeIf(v -> now.isAfter(v.expiresAt));
    }
}
