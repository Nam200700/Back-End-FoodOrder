package org.example.datn.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Định dạng thời gian cho các thông báo hiển thị TRỰC TIẾP cho người dùng.
 *
 * <p>Nối thẳng {@link LocalDateTime} vào chuỗi sẽ ra dạng ISO khó đọc
 * ("2026-08-15T15:26:58.270900"), nên mọi thông báo gửi ra FE cần đi qua đây.
 */
public final class DateTimeUtils {

    /** Ví dụ: "15:26 ngày 15/08/2026". */
    private static final DateTimeFormatter VI_DATE_TIME =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private DateTimeUtils() {
    }

    /** Định dạng mốc thời gian theo cách viết quen thuộc với người Việt. */
    public static String formatVi(LocalDateTime time) {
        return time == null ? "" : time.format(VI_DATE_TIME);
    }
}
