package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.request.food.CreateFoodRequest;
import org.example.datn.DTO.request.food.UpdateFoodRequest;
import org.example.datn.DTO.request.restaurant.CreateRestaurantRequest;
import org.example.datn.DTO.response.food.FoodResponse;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.DTO.request.restaurant.CreateCategoryRequest;
import org.example.datn.DTO.response.restaurant.CategoryResponse;
import org.example.datn.Service.CategoryService;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.FoodService;
import org.example.datn.Service.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class MerchantRestaurantController {

    private final RestaurantService restaurantService;
    private final FoodService foodService;
    private final CategoryService categoryService;

    @PostMapping("/restaurants")
    public ResponseEntity<ApiResponse<RestaurantResponse>> createRestaurant(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateRestaurantRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(restaurantService.create(user.getUserId(), req)));
    }

    @PutMapping("/restaurants/{restaurantId}")
    public ResponseEntity<ApiResponse<RestaurantResponse>> updateRestaurant(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateRestaurantRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.update(user.getUserId(), restaurantId, req)));
    }

    @PostMapping("/restaurants/{restaurantId}/foods")
    public ResponseEntity<ApiResponse<FoodResponse>> createFood(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateFoodRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(foodService.create(user.getUserId(), restaurantId, req)));
    }

    @GetMapping("/restaurants/{restaurantId}/foods")
    public ResponseEntity<ApiResponse<java.util.List<FoodResponse>>> getMerchantMenu(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(foodService.getMerchantMenu(user.getUserId(), restaurantId)));
    }

    @PutMapping("/foods/{foodId}")
    public ResponseEntity<ApiResponse<FoodResponse>> updateFood(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long foodId,
            @Valid @RequestBody UpdateFoodRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(foodService.update(user.getUserId(), foodId, req)));
    }

    @DeleteMapping("/foods/{foodId}")
    public ResponseEntity<ApiResponse<Void>> deleteFood(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long foodId) {
        foodService.delete(user.getUserId(), foodId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa món"));
    }

    @GetMapping("/restaurant")
    public ResponseEntity<ApiResponse<RestaurantResponse>> getMyRestaurant(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.getByOwnerId(user.getUserId())));
    }

    @PostMapping("/restaurants/{restaurantId}/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(categoryService.create(user.getUserId(), restaurantId, req)));
    }

    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long categoryId,
            @Valid @RequestBody CreateCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(user.getUserId(), categoryId, req)));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long categoryId) {
        categoryService.delete(user.getUserId(), categoryId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa danh mục"));
    }

    @PatchMapping("/restaurant/status")
    public ResponseEntity<ApiResponse<RestaurantResponse>> updateRestaurantStatus(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.updateStatus(user.getUserId(), status)));
    }
}
