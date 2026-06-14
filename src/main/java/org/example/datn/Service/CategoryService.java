package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Category;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.Food;
import org.example.datn.DTO.request.restaurant.CreateCategoryRequest;
import org.example.datn.DTO.response.restaurant.CategoryResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.CategoryMapper;
import org.example.datn.Repository.CategoryRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.FoodRepository;
import org.example.datn.security.OwnershipGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final FoodRepository foodRepository;
    private final CategoryMapper categoryMapper;
    private final OwnershipGuard ownershipGuard;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listByRestaurant(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        return categoryMapper.toResponseList(
                categoryRepository.findByRestaurantRestaurantIdOrderByDisplayOrderAsc(restaurantId));
    }

    @Transactional
    public CategoryResponse create(Long ownerId, Long restaurantId, CreateCategoryRequest req) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, ownerId);

        Category category = Category.builder()
                .restaurant(restaurant)
                .categoryName(req.getCategoryName())
                .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : 0)
                .build();

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long ownerId, Long categoryId, CreateCategoryRequest req) {
        Category category = categoryRepository.findByIdOrThrow(categoryId, ErrorCode.CATEGORY_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(category.getRestaurant(), ownerId);

        category.setCategoryName(req.getCategoryName());
        if (req.getDisplayOrder() != null) {
            category.setDisplayOrder(req.getDisplayOrder());
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long ownerId, Long categoryId) {
        Category category = categoryRepository.findByIdOrThrow(categoryId, ErrorCode.CATEGORY_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(category.getRestaurant(), ownerId);
        
        // Reset category_id = null cho các món ăn thuộc danh mục này
        List<Food> foods = foodRepository.findByCategoryCategoryId(categoryId);
        for (Food food : foods) {
            food.setCategory(null);
            foodRepository.save(food);
        }
        
        categoryRepository.delete(category);
    }
}
