package org.example.datn.util;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Diễn đạt thời gian cho các thông báo hiển thị TRỰC TIẾP cho người dùng.
 *
 * <p>Nối thẳng {@link LocalDateTime} vào chuỗi sẽ ra dạng ISO khó đọc
 * ("2026-08-15T15:26:58.270900"). Với thông báo khóa tạm thời, người dùng quan tâm
 * "còn phải chờ bao lâu" hơn là "đến mấy giờ" — nói theo thời lượng còn tránh được
 * hiểu nhầm khi đồng hồ máy người dùng lệch với đồng hồ máy chủ.
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
    }

    /**
     * Số giây còn lại từ bây giờ tới {@code until}, tối thiểu 0 (không trả số âm).
     * Dùng cho header {@code Retry-After} và cho FE đếm ngược.
     */
    public static int secondsUntil(LocalDateTime until) {
        if (until == null) return 0;
        long seconds = Duration.between(LocalDateTime.now(), until).getSeconds();
        return seconds <= 0 ? 0 : (int) Math.min(seconds, Integer.MAX_VALUE);
    }

    /**
     * Khoảng thời gian còn lại từ bây giờ tới {@code until}, viết theo lối tự nhiên:
     * "45 giây", "8 phút", "1 giờ 5 phút". Phút luôn được làm tròn LÊN để không bao giờ
     * hiện "0 phút" khi thực tế vẫn còn phải chờ.
     */
    public static String humanizeUntil(LocalDateTime until) {
        if (until == null) return "ít phút";

        long seconds = Duration.between(LocalDateTime.now(), until).getSeconds();
        if (seconds <= 0) return "vài giây";
        if (seconds < 60) return seconds + " giây";

        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return minutes + " phút";

        long hours = minutes / 60;
        long remainMinutes = minutes % 60;
        return remainMinutes == 0 ? hours + " giờ" : hours + " giờ " + remainMinutes + " phút";
    }
}
