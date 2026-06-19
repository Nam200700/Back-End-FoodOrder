package org.example.datn.Repository;

import org.example.datn.domain.FavoriteRestaurant;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteRestaurantRepository extends BaseRepository<FavoriteRestaurant, Long> {

    List<FavoriteRestaurant> findByCustomerUserId(Long customerId);

    boolean existsByCustomerUserIdAndRestaurantRestaurantId(Long customerId, Long restaurantId);

    @Modifying
    void deleteByCustomerUserIdAndRestaurantRestaurantId(Long customerId, Long restaurantId);
}
