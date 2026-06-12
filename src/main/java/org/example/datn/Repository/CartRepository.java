package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends BaseRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.food", "restaurant"})
    java.util.List<Cart> findByCustomerUserId(Long customerId);

    @EntityGraph(attributePaths = {"items", "items.food", "restaurant"})
    Optional<Cart> findByCustomerUserIdAndRestaurantRestaurantId(Long customerId, Long restaurantId);
}
