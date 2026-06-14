package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.DTO.response.food.FoodResponse;
import org.example.datn.DTO.response.restaurant.CategoryResponse;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Service.CategoryService;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.FoodService;
import org.example.datn.Service.RestaurantService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final FoodService foodService;
    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RestaurantResponse>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(restaurantService.listActive(pageable))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RestaurantResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(restaurantService.getById(id)));
    }

    @GetMapping("/{id}/foods")
    public ResponseEntity<ApiResponse<List<FoodResponse>>> menu(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(foodService.getMenu(id)));
    }

    @GetMapping("/{id}/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> categories(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listByRestaurant(id)));
    }
}
