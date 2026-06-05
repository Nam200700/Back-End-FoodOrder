package org.example.datn.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Helpers for the VNPay 2.1.0 payment flow: HMAC-SHA512 signing, building the
 * URL-encoded query string used both for the signature and the redirect URL.
 */
public final class VNPayUtils {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private VNPayUtils() {
    }

    public static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute HMAC-SHA512", e);
        }
    }

    /** Builds {@code key=urlEncode(value)&...} from an already-sorted map. */
    public static String buildEncodedData(Map<String, String> sortedParams) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : sortedParams.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            joiner.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return joiner.toString();
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public static String now() {
        return ZonedDateTime.now(VN_ZONE).format(DATE_FMT);
    }
}
