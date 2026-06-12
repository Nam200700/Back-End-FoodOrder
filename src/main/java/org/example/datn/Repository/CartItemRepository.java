package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.CartItem;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartItemRepository extends BaseRepository<CartItem, Long> {

    Optional<CartItem> findByCartCartIdAndFoodFoodId(Long cartId, Long foodId);
}
