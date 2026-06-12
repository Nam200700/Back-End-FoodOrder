package org.example.datn.Repository;

import org.example.datn.domain.Food;
import org.example.datn.Repository.base.BaseRepository;
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
}
