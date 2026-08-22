package org.example.datn.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Diễn đạt thời gian cho các thông báo hiển thị TRỰC TIẾP cho người dùng
 * và chuẩn hóa thời gian ở Việt Nam (Asia/Ho_Chi_Minh).
 */
public final class DateTimeUtils {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private DateTimeUtils() {
    }

    /**
     * Trả về LocalDateTime hiện tại theo múi giờ Việt Nam.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(VN_ZONE);
    }

    /**
     * Trả về LocalDate hiện tại theo múi giờ Việt Nam.
     */
    public static LocalDate nowDate() {
        return LocalDate.now(VN_ZONE);
    }

    /**
     * Trả về LocalTime hiện tại theo múi giờ Việt Nam.
     */
    public static LocalTime nowTime() {
        return LocalTime.now(VN_ZONE);
    }

    /**
     * Số giây còn lại từ bây giờ tới {@code until}, tối thiểu 0 (không trả số âm).
     * Dùng cho header {@code Retry-After} và cho FE đếm ngược.
     */
    public static int secondsUntil(LocalDateTime until) {
        if (until == null) return 0;
        long seconds = Duration.between(now(), until).getSeconds();
        return seconds <= 0 ? 0 : (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    /**
     * Khoảng thời gian còn lại từ bây giờ tới {@code until}, viết theo lối tự nhiên:
     * "45 giây", "8 phút", "1 giờ 5 phút". Phút luôn được làm tròn LÊN để không bao giờ
     * hiện "0 phút" khi thực tế vẫn còn phải chờ.
     */
    public static String humanizeUntil(LocalDateTime until) {
        if (until == null) return "ít phút";

        long seconds = Duration.between(now(), until).getSeconds();
        if (seconds <= 0) return "vài giây";
        if (seconds < 60) return seconds + " giây";

        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return minutes + " phút";

        long hours = minutes / 60;
        long remainMinutes = minutes % 60;
        return remainMinutes == 0 ? hours + " giờ" : hours + " giờ " + remainMinutes + " phút";
    }
}
