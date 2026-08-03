package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.food.FoodResponse;
import org.example.datn.Service.FoodService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Công khai — dùng cho trang Khám phá phía khách:
 *  - /foods/search : tìm món theo tên (server-side, giới hạn) → không cần nạp toàn bộ menu mọi quán.
 *  - /foods/popular: TOP món bán chạy thật (theo lượt đặt thật) cho mục "Món ăn xu hướng".
 */
@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FoodResponse>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(foodService.searchFoods(keyword, limit)));
    }

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<FoodResponse>>> popular(
            @RequestParam(required = false, defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(foodService.popularFoods(limit)));
    }
}
