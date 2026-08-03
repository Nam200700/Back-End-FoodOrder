package org.example.datn.Repository;

import org.example.datn.domain.Food;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodRepository extends BaseRepository<Food, Long> {

    /** Active menu of a restaurant, ordered by category then food id. */
    @Query("""
            SELECT f FROM Food f
            LEFT JOIN f.category c
            WHERE f.restaurant.restaurantId = :rid
              AND f.status = true
              AND f.isAvailable = true
            ORDER BY c.displayOrder, f.foodId
            """)
    List<Food> findActiveByRestaurantId(@Param("rid") Long restaurantId);

    @Query("""
            SELECT f FROM Food f
            LEFT JOIN f.category c
            WHERE f.restaurant.restaurantId = :rid
              AND f.status = true
            ORDER BY c.displayOrder, f.foodId
            """)
    List<Food> findByRestaurantIdForMerchant(@Param("rid") Long restaurantId);

    List<Food> findByRestaurantRestaurantId(Long restaurantId);

    List<Food> findByCategoryCategoryId(Long categoryId);

    /** Công khai: tìm món theo tên trên các quán đang mở (1 truy vấn, giới hạn qua Pageable) — cho trang Khám phá. */
    @Query("""
            SELECT f FROM Food f
            WHERE f.status = true AND f.isAvailable = true AND f.restaurant.status = true
              AND LOWER(f.foodName) LIKE LOWER(CONCAT('%', :kw, '%'))
            ORDER BY f.foodId DESC
            """)
    List<Food> searchActiveByName(@Param("kw") String keyword, Pageable pageable);

    /**
     * Công khai: TOP món bán chạy THẬT — gộp theo tổng số lượng đã bán từ đơn COMPLETED (1 truy vấn).
     * Trả về [Food, sold] để service map ra orderCount thật — thay cho việc nạp toàn bộ menu của mọi quán.
     */
    @Query("""
            SELECT f, COALESCE(SUM(oi.quantity), 0) AS sold
            FROM Order o
            JOIN o.items oi
            JOIN oi.food f
            WHERE o.orderStatus = org.example.datn.domain.enums.OrderStatus.COMPLETED
              AND f.status = true AND f.isAvailable = true AND f.restaurant.status = true
            GROUP BY f
            ORDER BY sold DESC
            """)
    List<Object[]> findPopularFoods(Pageable pageable);

    // ─── Đếm sức khoẻ thực đơn cho dashboard merchant ───
    long countByRestaurantRestaurantId(Long restaurantId);                                      // tổng số món
    long countByRestaurantRestaurantIdAndStatusTrueAndIsAvailableTrue(Long restaurantId);       // đang bán
    long countByRestaurantRestaurantIdAndStatusTrueAndIsAvailableFalse(Long restaurantId);      // hiện nhưng tạm hết
    long countByRestaurantRestaurantIdAndStatusFalse(Long restaurantId);                        // đang ẩn
}
