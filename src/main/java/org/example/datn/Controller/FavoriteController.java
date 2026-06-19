package org.example.datn.Controller;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favorites")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RestaurantResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(favoriteService.list(user.getUserId())));
    }

    @PostMapping("/{restaurantId}")
    public ResponseEntity<ApiResponse<Void>> add(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long restaurantId) {
        favoriteService.add(user.getUserId(), restaurantId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã thêm vào yêu thích"));
    }

    @DeleteMapping("/{restaurantId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long restaurantId) {
        favoriteService.remove(user.getUserId(), restaurantId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã xóa khỏi yêu thích"));
    }
}
