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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** Công khai: tìm món theo tên (giới hạn số kết quả) — trang Khám phá tìm kiếm server-side, không nạp toàn bộ menu. */
    @Transactional(readOnly = true)
    public List<FoodResponse> searchFoods(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        List<Food> foods = foodRepository.searchActiveByName(keyword.trim(), PageRequest.of(0, Math.max(1, Math.min(limit, 50))));
        return foodMapper.toResponseList(foods);
    }

    /** Công khai: TOP món bán chạy thật (số lượt đặt thật) — cho mục "Món ăn xu hướng". Một truy vấn gộp + 1 lần nạp món. */
    @Transactional(readOnly = true)
    public List<FoodResponse> popularFoods(int limit) {
        List<Object[]> rows = foodRepository.findPopularFoodIds(PageRequest.of(0, Math.max(1, Math.min(limit, 20))));
        if (rows.isEmpty()) return List.of();

        List<Long> ids = new ArrayList<>();
        for (Object[] row : rows) ids.add(((Number) row[0]).longValue());

        java.util.Map<Long, Food> byId = new java.util.HashMap<>();
        for (Food f : foodRepository.findAllById(ids)) byId.put(f.getFoodId(), f);

        List<FoodResponse> out = new ArrayList<>();
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            Food food = byId.get(id);
            if (food == null) continue; // giữ đúng thứ tự bán chạy giảm dần
            FoodResponse res = foodMapper.toResponse(food);
            res.setOrderCount(((Number) row[1]).intValue());
            out.add(res);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> getMenu(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new AppException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        List<Food> foods = foodRepository.findActiveByRestaurantId(restaurantId);
        return withSoldCount(foods);
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> getMerchantMenu(Long ownerId, Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        ownershipGuard.checkRestaurantOwner(restaurant, ownerId);

        List<Food> foods = foodRepository.findByRestaurantIdForMerchant(restaurantId);
        return withSoldCount(foods);
    }

    /**
     * Map danh sách món + gắn số "đã bán" (SUM quantity đơn hoàn tất) bằng 1 câu gộp
     * theo danh sách foodId thay vì 1 query MỖI món → khử N+1 khi dựng menu.
     */
    private List<FoodResponse> withSoldCount(List<Food> foods) {
        List<FoodResponse> list = foodMapper.toResponseList(foods);
        if (foods.isEmpty()) return list;

        List<Long> foodIds = foods.stream().map(Food::getFoodId).toList();
        Map<Long, Integer> soldMap = new HashMap<>();
        for (Object[] row : orderRepository.sumCompletedQuantityByFoodIds(foodIds)) {
            soldMap.put((Long) row[0], ((Number) row[1]).intValue());
        }

        for (int i = 0; i < foods.size(); i++) {
            FoodResponse res = list.get(i);
            res.setIsAvailable(foods.get(i).getIsAvailable());
            res.setOrderCount(soldMap.getOrDefault(res.getId(), 0));
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
