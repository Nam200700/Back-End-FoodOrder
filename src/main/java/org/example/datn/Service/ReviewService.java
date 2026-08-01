package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Order;
import org.example.datn.domain.Review;
import org.example.datn.domain.ReviewImage;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.DTO.request.review.CreateReviewRequest;
import org.example.datn.DTO.response.review.ReviewResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.ReviewNotAllowedException;
import org.example.datn.mapper.ReviewMapper;
import org.example.datn.domain.Shipper;
import org.example.datn.Repository.OrderRepository;
import org.example.datn.Repository.ReviewRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.ShipperRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int REVIEW_WINDOW_DAYS = 7;

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;
    private final ShipperRepository shipperRepository;

    @Transactional
    public ReviewResponse createReview(Long customerId, CreateReviewRequest req) {
        
        Order order = orderRepository.findByIdOrThrow(req.getOrderId(), ErrorCode.ORDER_NOT_FOUND);

        if (!order.getCustomer().getUserId().equals(customerId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (order.getOrderStatus() != OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.REVIEW_NOT_ALLOWED, "Chỉ đánh giá đơn đã hoàn thành");
        }
        if (order.getCompletedAt() != null
                && order.getCompletedAt().plusDays(REVIEW_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.REVIEW_NOT_ALLOWED, "Đã quá ngày để đánh giá");
        }
        if (reviewRepository.existsByOrderOrderId(req.getOrderId())) {
            throw new AppException(ErrorCode.REVIEW_EXISTS);
        }

        Shipper shipper = null;
        if (order.getShipper() != null) {
            shipper = shipperRepository.findByUserUserId(order.getShipper().getUserId()).orElse(null);
        }

        Review review = Review.builder()
                .order(order)
                .customer(userRepository.getReferenceById(customerId))
                .restaurant(order.getRestaurant())
                .restaurantRating(req.getRestaurantRating())
                .restaurantComment(req.getRestaurantComment())
                .shipperRating(req.getShipperRating())
                .shipperComment(req.getShipperComment())
                .shipper(shipper)
                .build();

        if (req.getImages() != null || !req.getImages().isEmpty()) {
            List<ReviewImage> reviewImages = new ArrayList<>();
            for (int i = 0; i < req.getImages().size(); i++) {
                String imageUrl = req.getImages().get(i);
                if (imageUrl != null && !imageUrl.isBlank()) {
                    ReviewImage reviewImage = ReviewImage.builder()
                            .review(review)
                            .imageUrl(imageUrl)
                            .displayOrder(i)
                            .build();
                    reviewImages.add(reviewImage);
                }
            }
            review.setImages(reviewImages);
        }

        Review savedReview = reviewRepository.save(review);

        // Tính toán lại avgRating của Shipper nếu có rating cho Shipper
        if (shipper != null && req.getShipperRating() != null) {
            Double avgRating = reviewRepository.findAverageRatingByShipperId(shipper.getShipperId());
            if (avgRating != null) {
                // Làm tròn avg_rating đến 2 chữ số thập phân
                double rounded = Math.round(avgRating * 100.0) / 100.0;
                shipper.setAvgRating(java.math.BigDecimal.valueOf(rounded));
                shipperRepository.save(shipper);
            }
        }

        return reviewMapper.toResponse(savedReview);
    }

    @Transactional
    public ReviewResponse replyReview(Long merchantId, Long reviewId, String reply) {
        Review review = reviewRepository.findByIdOrThrow(reviewId, ErrorCode.NOT_FOUND);
        if (!review.getRestaurant().getOwner().getUserId().equals(merchantId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        review.setMerchantReply(reply);
        review.setRepliedAt(LocalDateTime.now());
        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getRestaurantReviews(Long restaurantId, Pageable pageable) {
        return PageResponse.from(reviewRepository
                .findByRestaurantRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable)
                .map(reviewMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReviewByOrderId(Long customerId, Long orderId) {
        Review review = reviewRepository.findByOrderOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!review.getCustomer().getUserId().equals(customerId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return reviewMapper.toResponse(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getShipperReviews(Long shipperUserId, Pageable pageable) {
        Shipper shipper = shipperRepository.findByUserUserId(shipperUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy shipper"));

        return PageResponse.from(reviewRepository
                .findByShipperShipperIdOrderByCreatedAtDesc(shipper.getShipperId(), pageable)
                .map(reviewMapper::toResponse));
    }
}
