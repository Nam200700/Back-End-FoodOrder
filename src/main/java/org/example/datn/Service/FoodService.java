package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Category;
import org.example.datn.domain.Food;
import org.example.datn.domain.Restaurant;
import org.example.datn.DTO.request.food.CreateFoodRequest;
import org.example.datn.DTO.request.food.UpdateFoodRequest;
import org.example.datn.DTO.response.food.FoodResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.FoodMapper;
import org.example.datn.Repository.CategoryRepository;
import org.example.datn.Repository.FoodRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.OrderRepository;
import org.example.datn.security.OwnershipGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodService {

    private final FoodRepository foodRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final FoodMapper foodMapper;
    private final OrderRepository orderRepository;
    private final OwnershipGuard ownershipGuard;
    private final ImageUploadService imageUploadService;

    @Transactional(readOnly = true)
    public List<FoodResponse> getMenu(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        List<Food> foods = foodRepository.findActiveByRestaurantId(restaurantId);
        List<FoodResponse> list = foodMapper.toResponseList(foods);
        for (int i = 0; i < foods.size(); i++) {
            Food foodEntity = foods.get(i);
            FoodResponse res = list.get(i);
            res.setIsAvailable(foodEntity.getIsAvailable());
            Integer count = orderRepository.countCompletedQuantityByFoodId(res.getId());
            res.setOrderCount(count != null ? count : 0);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> getMerchantMenu(Long ownerId, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, ownerId);

        List<Food> foods = foodRepository.findByRestaurantIdForMerchant(restaurantId);
        List<FoodResponse> list = foodMapper.toResponseList(foods);
        for (int i = 0; i < foods.size(); i++) {
            Food foodEntity = foods.get(i);
            FoodResponse res = list.get(i);
            res.setIsAvailable(foodEntity.getIsAvailable());
            Integer count = orderRepository.countCompletedQuantityByFoodId(res.getId());
            res.setOrderCount(count != null ? count : 0);
        }
        return list;
    }

    @Transactional
    public FoodResponse create(Long ownerId, Long restaurantId, CreateFoodRequest req) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, ownerId);

        Food food = foodMapper.toEntity(req);
        food.setRestaurant(restaurant);
        food.setCategory(resolveCategory(req.getCategoryId(), restaurantId));
        food.setStatus(true);
        food.setIsAvailable(true);
        Food saved = foodRepository.save(food);
        FoodResponse resp = foodMapper.toResponse(saved);
        resp.setIsAvailable(saved.getIsAvailable());
        resp.setOrderCount(0);
        return resp;
    }

    @Transactional
    public FoodResponse update(Long ownerId, Long foodId, UpdateFoodRequest req) {
        Food food = foodRepository.findByIdOrThrow(foodId, ErrorCode.FOOD_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(food.getRestaurant(), ownerId);

        String oldImageUrl = food.getImageUrl();

        foodMapper.updateEntity(req, food);

        if (req.getIsAvailable() != null) {
            food.setIsAvailable(req.getIsAvailable());
        }

        if (req.getImageUrl() != null) {
            String newImageUrl = req.getImageUrl().trim();
            if (!newImageUrl.equals(oldImageUrl)) {
                if (oldImageUrl != null && !oldImageUrl.trim().isEmpty()) {
                    imageUploadService.deleteImage(oldImageUrl);
                }
            }
        }

        if (req.getCategoryId() != null) {
            food.setCategory(resolveCategory(req.getCategoryId(), food.getRestaurant().getRestaurantId()));
        }
        Food saved = foodRepository.save(food);
        FoodResponse resp = foodMapper.toResponse(saved);
        resp.setIsAvailable(saved.getIsAvailable());
        Integer count = orderRepository.countCompletedQuantityByFoodId(resp.getId());
        resp.setOrderCount(count != null ? count : 0);
        return resp;
    }

    @Transactional
    public void delete(Long ownerId, Long foodId) {
        Food food = foodRepository.findByIdOrThrow(foodId, ErrorCode.FOOD_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(food.getRestaurant(), ownerId);
        food.setStatus(false);
        foodRepository.save(food);
    }

    private Category resolveCategory(Long categoryId, Long restaurantId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findByIdOrThrow(categoryId, ErrorCode.CATEGORY_NOT_FOUND);
        if (!category.getRestaurant().getRestaurantId().equals(restaurantId)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND, "Danh mục không thuộc quán này");
        }
        return category;
    }
}
