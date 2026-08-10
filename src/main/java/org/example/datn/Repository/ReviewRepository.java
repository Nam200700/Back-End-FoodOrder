package org.example.datn.Repository;

import org.example.datn.domain.Review;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends BaseRepository<Review, Long> {

    boolean existsByOrderOrderId(Long orderId);

    Optional<Review> findByOrderOrderId(Long orderId);

    /** Lấy review của nhiều đơn cùng lúc (1 câu IN) — khử N+1 khi enrich danh sách đơn. */
    List<Review> findByOrderOrderIdIn(java.util.Collection<Long> orderIds);

    Page<Review> findByRestaurantRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    Page<Review> findByShipperShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);

    long countByRestaurantRestaurantId(Long restaurantId);

    @Query("SELECT AVG(r.restaurantRating) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId")
    Double findAverageRatingByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT AVG(r.shipperRating) FROM Review r WHERE r.shipper.shipperId = :shipperId AND r.shipperRating IS NOT NULL")
    Double findAverageRatingByShipperId(@Param("shipperId") Long shipperId);

    // Số lượt đã đánh giá tài xế (có chấm sao) — cho chỉ số "Từ N đánh giá"
    long countByShipperShipperIdAndShipperRatingIsNotNull(Long shipperId);

    // ─────────── Trang Đánh Giá tài xế: gộp tóm tắt server-side + phân trang có lọc ───────────

    /** Toàn bộ review CÓ chấm sao của 1 shipper (chỉ đọc rating + comment → không kích hoạt lazy images). */
    List<Review> findByShipperShipperIdAndShipperRatingIsNotNull(Long shipperId);

    /** Đếm review của shipper CÓ kèm ảnh (cho nút lọc "Có ảnh (N)"). */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.shipper.shipperId = :sid AND r.shipperRating IS NOT NULL AND SIZE(r.images) > 0")
    long countShipperReviewsWithImages(@Param("sid") Long sid);

    /** Danh sách review của shipper có LỌC sao/ảnh + SORT (theo Pageable) — phân trang thật. */
    @Query("""
            SELECT r FROM Review r
            WHERE r.shipper.shipperId = :sid AND r.shipperRating IS NOT NULL
              AND (:star IS NULL OR r.shipperRating = :star)
              AND (:imageOnly = false OR SIZE(r.images) > 0)
            """)
    Page<Review> findShipperReviewsFiltered(@Param("sid") Long sid,
                                            @Param("star") Integer star,
                                            @Param("imageOnly") boolean imageOnly,
                                            Pageable pageable);

    // ─────────── Trang Đánh Giá quán (owner): gộp tóm tắt server-side + phân trang có lọc ───────────

    /** Toàn bộ review CÓ chấm sao quán của 1 nhà hàng. */
    List<Review> findByRestaurantRestaurantIdAndRestaurantRatingIsNotNull(Long restaurantId);

    /** Đếm review của quán CÓ kèm ảnh (cho nút lọc "Có ảnh (N)"). */
    @Query("SELECT COUNT(r) FROM Review r WHERE r.restaurant.restaurantId = :rid AND r.restaurantRating IS NOT NULL AND SIZE(r.images) > 0")
    long countRestaurantReviewsWithImages(@Param("rid") Long rid);

    /** Danh sách review của quán có LỌC sao/ảnh + SORT (theo Pageable) — phân trang thật. */
    @Query("""
            SELECT r FROM Review r
            WHERE r.restaurant.restaurantId = :rid AND r.restaurantRating IS NOT NULL
              AND (:star IS NULL OR r.restaurantRating = :star)
              AND (:imageOnly = false OR SIZE(r.images) > 0)
            """)
    Page<Review> findRestaurantReviewsFiltered(@Param("rid") Long rid,
                                               @Param("star") Integer star,
                                               @Param("imageOnly") boolean imageOnly,
                                               Pageable pageable);
}
