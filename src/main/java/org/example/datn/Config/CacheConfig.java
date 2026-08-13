package org.example.datn.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache-aside cho các endpoint thống kê (đọc nhiều, tính nặng).
 * Dùng Caffeine in-memory — KHÔNG cần Redis/hạ tầng ngoài.
 *
 * Độ tươi số liệu KHÔNG dựa vào TTL: mọi thống kê bắt nguồn từ bảng orders nên các
 * method ghi (đơn/thanh toán) gắn {@code @EvictStatsCaches} sẽ xóa cache ngay khi có
 * thay đổi → đọc kế tiếp tính lại số mới. TTL 60s chỉ là lưới an toàn cho vài số
 * KHÔNG do đơn chi phối (tăng trưởng user, sức khoẻ thực đơn).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "adminOverview", "adminInsights", "adminReport", "merchantStats", "merchantInsights", "merchantReport", "voucherAnalytics",
                "shipperInsights",
                // ── Feed công khai của khách (đọc rất nhiều, chung mọi user) ──
                "restaurantFeed",  // /restaurants feed không từ khoá (trang chủ + Khám phá)
                "popularFoods",    // /foods/popular — top món xu hướng
                "routeInfo");      // tuyến đường ORS theo cặp toạ độ (phí ship giỏ hàng)
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(500));
        return manager;
    }
}
