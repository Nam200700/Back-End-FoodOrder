package org.example.datn.annotation;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation gộp: xóa SẠCH cache thống kê khi có thay đổi dữ liệu đơn/thanh toán.
 * Đặt 1 chỗ để tránh rải @CacheEvict lẻ khắp nơi (dễ sót → số cũ).
 *
 * Spring cache infra nhận diện qua meta-annotation (AnnotatedElementUtils), nên
 * chỉ cần gắn @EvictStatsCaches lên method ghi là tương đương @CacheEvict bên dưới.
 * allEntries=true: mọi khách/kỳ đều được làm mới ngay (đơn giản & chắc chắn tươi).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@CacheEvict(cacheNames = {"adminInsights", "adminReport", "merchantStats", "merchantInsights", "merchantReport", "voucherAnalytics",
        "shipperInsights"},
        allEntries = true)
public @interface EvictStatsCaches {
}
