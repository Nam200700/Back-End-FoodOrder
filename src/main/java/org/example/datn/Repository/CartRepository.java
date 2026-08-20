package org.example.datn.Repository;

import jakarta.persistence.LockModeType;
import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Repository
public interface CartRepository extends BaseRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.food", "restaurant"})
    java.util.List<Cart> findByCustomerUserIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"items", "items.food", "restaurant"})
    Optional<Cart> findByCustomerUserIdAndRestaurantRestaurantId(Long customerId, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.customer.userId = :customerId AND c.restaurant.restaurantId = :restaurantId")
    Optional<Cart> findByCustomerAndRestaurantForUpdate(@Param("customerId") Long customerId, @Param("restaurantId") Long restaurantId);

}
