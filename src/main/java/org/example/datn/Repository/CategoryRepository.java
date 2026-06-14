package org.example.datn.Repository;

import org.example.datn.domain.Category;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends BaseRepository<Category, Long> {

    List<Category> findByRestaurantRestaurantIdOrderByDisplayOrderAsc(Long restaurantId);
}
