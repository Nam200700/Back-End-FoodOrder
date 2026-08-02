package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.DTO.request.review.CreateReviewRequest;
import org.example.datn.DTO.request.review.ReplyReviewRequest;
import org.example.datn.DTO.response.review.ReviewResponse;
import org.example.datn.DTO.response.review.ShipperReviewSummaryResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.ReviewService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateReviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reviewService.createReview(user.getUserId(), req)));
    }

    @GetMapping("/reviews/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewByOrder(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getReviewByOrderId(user.getUserId(), orderId)));
    }

    @PostMapping("/merchant/reviews/{reviewId}/reply")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> reply(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReplyReviewRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.replyReview(user.getUserId(), reviewId, req.getReply())));
    }

    /** Public: reviews of a restaurant — lọc sao/ảnh + sắp xếp + phân trang (server-side) cho trang chi tiết quán. */
    @GetMapping("/restaurants/{restaurantId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> restaurantReviews(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) Integer star,
            @RequestParam(required = false, defaultValue = "false") boolean imageOnly,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.getRestaurantReviewsFiltered(restaurantId, star, imageOnly, pageable)));
    }

    /** Public: tóm tắt đánh giá của quán (điểm TB, phân bố sao, % hài lòng, khen/chê) cho trang chi tiết quán. */
    @GetMapping("/restaurants/{restaurantId}/reviews/summary")
    public ResponseEntity<ApiResponse<ShipperReviewSummaryResponse>> restaurantReviewSummary(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.restaurantReviewSummary(restaurantId)));
    }

    /** Đánh giá QUÁN của owner đang đăng nhập — lọc sao/ảnh + sắp xếp + phân trang (server-side). */
    @GetMapping("/merchant/reviews")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> merchantReviews(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer star,
            @RequestParam(required = false, defaultValue = "false") boolean imageOnly,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.getMerchantReviews(user.getUserId(), star, imageOnly, pageable)));
    }

    /** Tóm tắt đánh giá quán (gộp server-side) cho trang Đánh Giá owner. */
    @GetMapping("/merchant/reviews/summary")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ShipperReviewSummaryResponse>> merchantReviewSummary(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.merchantReviewSummary(user.getUserId())));
    }

    @GetMapping("/shipper/reviews")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> shipperReviews(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer star,
            @RequestParam(required = false, defaultValue = "false") boolean imageOnly,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.getShipperReviews(user.getUserId(), star, imageOnly, pageable)));
    }

    /** Tóm tắt đánh giá tài xế (gộp server-side) — trang Đánh Giá dùng để không phải tải hết. */
    @GetMapping("/shipper/reviews/summary")
    @PreAuthorize("hasRole('SHIPPER')")
    public ResponseEntity<ApiResponse<ShipperReviewSummaryResponse>> shipperReviewSummary(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.shipperReviewSummary(user.getUserId())));
    }
}
