package org.example.datn.Repository;

import org.example.datn.domain.Review;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface ReviewRepository extends BaseRepository<Review, Long> {

    boolean existsByOrderOrderId(Long orderId);

    Optional<Review> findByOrderOrderId(Long orderId);

    Page<Review> findByRestaurantRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    Page<Review> findByShipperShipperIdOrderByCreatedAtDesc(Long shipperId, Pageable pageable);

    long countByRestaurantRestaurantId(Long restaurantId);

    @Query("SELECT AVG(r.restaurantRating) FROM Review r WHERE r.restaurant.restaurantId = :restaurantId")
    Double findAverageRatingByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT AVG(r.shipperRating) FROM Review r WHERE r.shipper.shipperId = :shipperId AND r.shipperRating IS NOT NULL")
    Double findAverageRatingByShipperId(@Param("shipperId") Long shipperId);

    // Số lượt đã đánh giá tài xế (có chấm sao) — cho chỉ số "Từ N đánh giá"
    long countByShipperShipperIdAndShipperRatingIsNotNull(Long shipperId);
}
