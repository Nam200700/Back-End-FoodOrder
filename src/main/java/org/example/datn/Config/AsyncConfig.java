package org.example.datn.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} for fire-and-forget work such as notification
 * broadcasting, so request threads are not blocked.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }

    /**
     * Pool RIÊNG cho gửi email OTP (SMTP chậm/blocking). Tách khỏi notificationExecutor
     * để lời gọi SMTP không chiếm chỗ của broadcast thông báo. Nhờ vậy register/quên mật khẩu
     * trả về ngay, không giữ transaction DB mở chờ gửi mail.
     */
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }

    /**
     * Pool RIÊNG cho việc xoá ảnh cũ trên Cloudinary (gọi HTTP tới bên thứ ba, best-effort).
     * Nhờ vậy request cập nhật avatar/món/quán trả về NGAY, không chờ round-trip destroy
     * và không giữ transaction DB mở trong lúc gọi mạng.
     */
    @Bean(name = "imageExecutor")
    public Executor imageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("image-");
        executor.initialize();
        return executor;
    }
}
