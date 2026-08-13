package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Order;
import org.example.datn.domain.Review;
import org.example.datn.domain.ReviewImage;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.DTO.request.review.CreateReviewRequest;
import org.example.datn.DTO.response.review.ReviewResponse;
import org.example.datn.DTO.response.review.ShipperReviewSummaryResponse;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Exception.ReviewNotAllowedException;
import org.example.datn.mapper.ReviewMapper;
import org.example.datn.domain.Shipper;
import org.example.datn.domain.Restaurant;
import org.example.datn.Repository.OrderRepository;
import org.example.datn.Repository.ReviewRepository;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.ShipperRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int REVIEW_WINDOW_DAYS = 7;

    // ── Phân loại cụm nhận xét KHEN/CẦN CẢI THIỆN — ĐỒNG BỘ với FE (ShipperReviews.jsx) ──
    private static final Set<String> POS_TAGS = Set.of(
            "giao hàng nhanh", "giao đúng giờ", "tài xế thân thiện", "vui vẻ nhiệt tình",
            "cẩn thận với món", "đóng gói nguyên vẹn", "gọi điện lịch sự", "giao tận nơi", "chuyên nghiệp");
    private static final Set<String> NEG_TAGS = Set.of(
            "giao hàng trễ", "thái độ chưa tốt", "làm rơi/hỏng món", "khó liên lạc",
            "giao sai địa chỉ", "không gọi trước", "món bị xáo trộn", "thiếu chuyên nghiệp");
    private static final Set<String> NEU_TAGS = Set.of("tạm ổn", "bình thường", "giao đúng nơi", "liên lạc được");
    private static final Pattern NEG_RE = Pattern.compile(
            "trễ|chậm|hỏng|rơi|sai|khó|thiếu|tệ|kém|lâu|xáo trộn|chưa tốt|thái độ|thô lỗ|bất lịch sự|không gọi|quên");

    /** 'good' = khen · 'bad' = cần cải thiện · 'skip' = trung tính. */
    private static String classifyPhrase(String key) {
        if (POS_TAGS.contains(key)) return "good";
        if (NEG_TAGS.contains(key) || NEG_RE.matcher(key).find()) return "bad";
        if (NEU_TAGS.contains(key)) return "skip";
        return "good"; // nhận xét tự do mặc định coi là tích cực
    }

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;
    private final ShipperRepository shipperRepository;
    private final RestaurantRepository restaurantRepository;

    // Review mới làm đổi mọi bản tóm tắt (phân bố sao, TB, lời khen…) và rating quán ở feed.
    @Caching(evict = {
            @CacheEvict(value = "reviewSummaryShipper", allEntries = true),
            @CacheEvict(value = "reviewSummaryMerchant", allEntries = true),
            @CacheEvict(value = "reviewSummaryRestaurant", allEntries = true),
            @CacheEvict(value = "restaurantFeed", allEntries = true),
    })
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

        if (req.getImages() != null && !req.getImages().isEmpty()) {
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

    /** Công khai: danh sách đánh giá của quán có LỌC sao/ảnh + SORT (server-side) — dùng cho trang chi tiết quán. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getRestaurantReviewsFiltered(Long restaurantId, Integer star, boolean imageOnly, Pageable pageable) {
        return PageResponse.from(reviewRepository
                .findRestaurantReviewsFiltered(restaurantId, star, imageOnly, pageable)
                .map(reviewMapper::toResponse));
    }

    /** Công khai: tóm tắt đánh giá của một quán (theo restaurantId) — cho trang chi tiết quán phía khách. */
    @Cacheable(value = "reviewSummaryRestaurant", key = "#restaurantId")
    @Transactional(readOnly = true)
    public ShipperReviewSummaryResponse restaurantReviewSummary(Long restaurantId) {
        return buildRestaurantSummary(restaurantId);
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
    public PageResponse<ReviewResponse> getShipperReviews(Long shipperUserId, Integer star, boolean imageOnly, Pageable pageable) {
        Shipper shipper = shipperRepository.findByUserUserId(shipperUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy shipper"));

        return PageResponse.from(reviewRepository
                .findShipperReviewsFiltered(shipper.getShipperId(), star, imageOnly, pageable)
                .map(reviewMapper::toResponse));
    }

    /**
     * TÓM TẮT đánh giá tài xế — gộp toàn bộ ở server (điểm TB, phân bố sao, % hài lòng,
     * 30 ngày gần đây, lời khen/điểm cần cải thiện). Trả payload nhỏ; danh sách review phân trang riêng.
     */
    @Cacheable(value = "reviewSummaryShipper", key = "#shipperUserId")
    @Transactional(readOnly = true)
    public ShipperReviewSummaryResponse shipperReviewSummary(Long shipperUserId) {
        Shipper shipper = shipperRepository.findByUserUserId(shipperUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy shipper"));
        Long sid = shipper.getShipperId();

        List<Review> reviews = reviewRepository.findByShipperShipperIdAndShipperRatingIsNotNull(sid);
        long total = reviews.size();
        long[] dist = new long[6]; // dùng index 1..5
        double sum = 0;
        long positive = 0, recentCount = 0;
        double recentSum = 0;
        LocalDateTime since30 = LocalDateTime.now().minusDays(30);

        Map<String, Long> goodCount = new HashMap<>(), badCount = new HashMap<>();
        Map<String, String> goodText = new HashMap<>(), badText = new HashMap<>();

        for (Review r : reviews) {
            int rating = r.getShipperRating();
            if (rating >= 1 && rating <= 5) dist[rating]++;
            sum += rating;
            if (rating >= 4) positive++;
            if (r.getCreatedAt() != null && !r.getCreatedAt().isBefore(since30)) { recentCount++; recentSum += rating; }

            String c = r.getShipperComment();
            if (c == null) continue;
            for (String raw : c.split("[,;·|/]+")) {
                String t = raw.trim();
                if (t.length() < 3 || t.length() > 30) continue; // bỏ câu quá dài (không phải nhãn)
                String key = t.toLowerCase();
                String band = classifyPhrase(key);
                if ("skip".equals(band)) continue;
                if ("bad".equals(band)) { badCount.merge(key, 1L, Long::sum); badText.putIfAbsent(key, t); }
                else { goodCount.merge(key, 1L, Long::sum); goodText.putIfAbsent(key, t); }
            }
        }

        List<ShipperReviewSummaryResponse.StarCount> distribution = new ArrayList<>();
        for (int s = 5; s >= 1; s--)
            distribution.add(ShipperReviewSummaryResponse.StarCount.builder().star(s).count(dist[s]).build());

        return ShipperReviewSummaryResponse.builder()
                .total(total)
                .avg(total > 0 ? sum / total : 0)
                .distribution(distribution)
                .positiveCount(positive)
                .recentCount(recentCount)
                .recentAvg(recentCount > 0 ? recentSum / recentCount : 0)
                .withImageCount(reviewRepository.countShipperReviewsWithImages(sid))
                .compliments(topPhrases(goodCount, goodText, 8))
                .complaints(topPhrases(badCount, badText, 6))
                .build();
    }

    /** Danh sách review của quán có LỌC sao/ảnh + SORT — resolve nhà hàng từ owner đăng nhập. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getMerchantReviews(Long ownerUserId, Integer star, boolean imageOnly, Pageable pageable) {
        Long rid = resolveOwnerRestaurantId(ownerUserId);
        return PageResponse.from(reviewRepository
                .findRestaurantReviewsFiltered(rid, star, imageOnly, pageable)
                .map(reviewMapper::toResponse));
    }

    /**
     * TÓM TẮT đánh giá QUÁN — gộp toàn bộ ở server (điểm TB, phân bố sao, % hài lòng,
     * 30 ngày gần đây, lời khen/điểm cần cải thiện). Trả payload nhỏ; danh sách phân trang riêng.
     */
    @Cacheable(value = "reviewSummaryMerchant", key = "#ownerUserId")
    @Transactional(readOnly = true)
    public ShipperReviewSummaryResponse merchantReviewSummary(Long ownerUserId) {
        return buildRestaurantSummary(resolveOwnerRestaurantId(ownerUserId));
    }

    /** Gộp tóm tắt đánh giá của một quán theo restaurantId — dùng chung cho owner + trang chi tiết quán. */
    private ShipperReviewSummaryResponse buildRestaurantSummary(Long rid) {
        List<Review> reviews = reviewRepository.findByRestaurantRestaurantIdAndRestaurantRatingIsNotNull(rid);
        long total = reviews.size();
        long[] dist = new long[6]; // index 1..5
        double sum = 0;
        long positive = 0, recentCount = 0;
        double recentSum = 0;
        LocalDateTime since30 = LocalDateTime.now().minusDays(30);

        Map<String, Long> goodCount = new HashMap<>(), badCount = new HashMap<>();
        Map<String, String> goodText = new HashMap<>(), badText = new HashMap<>();

        for (Review r : reviews) {
            int rating = r.getRestaurantRating();
            if (rating >= 1 && rating <= 5) dist[rating]++;
            sum += rating;
            if (rating >= 4) positive++;
            if (r.getCreatedAt() != null && !r.getCreatedAt().isBefore(since30)) { recentCount++; recentSum += rating; }

            String c = r.getRestaurantComment();
            if (c == null) continue;
            for (String raw : c.split("[,;·|/]+")) {
                String t = raw.trim();
                if (t.length() < 3 || t.length() > 30) continue;
                String key = t.toLowerCase();
                String band = classifyPhrase(key);
                if ("skip".equals(band)) continue;
                if ("bad".equals(band)) { badCount.merge(key, 1L, Long::sum); badText.putIfAbsent(key, t); }
                else { goodCount.merge(key, 1L, Long::sum); goodText.putIfAbsent(key, t); }
            }
        }

        List<ShipperReviewSummaryResponse.StarCount> distribution = new ArrayList<>();
        for (int s = 5; s >= 1; s--)
            distribution.add(ShipperReviewSummaryResponse.StarCount.builder().star(s).count(dist[s]).build());

        return ShipperReviewSummaryResponse.builder()
                .total(total)
                .avg(total > 0 ? sum / total : 0)
                .distribution(distribution)
                .positiveCount(positive)
                .recentCount(recentCount)
                .recentAvg(recentCount > 0 ? recentSum / recentCount : 0)
                .withImageCount(reviewRepository.countRestaurantReviewsWithImages(rid))
                .compliments(topPhrases(goodCount, goodText, 8))
                .complaints(topPhrases(badCount, badText, 6))
                .build();
    }

    /** Lấy id nhà hàng của owner đang đăng nhập (mỗi owner 1 quán trong hệ thống hiện tại). */
    private Long resolveOwnerRestaurantId(Long ownerUserId) {
        return restaurantRepository.findByOwnerUserId(ownerUserId).stream().findFirst()
                .map(Restaurant::getRestaurantId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy nhà hàng của bạn"));
    }

    /** Sắp xếp cụm theo tần suất giảm dần, lấy tối đa n cụm. */
    private static List<ShipperReviewSummaryResponse.Phrase> topPhrases(Map<String, Long> count, Map<String, String> text, int n) {
        return count.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(e -> ShipperReviewSummaryResponse.Phrase.builder()
                        .text(text.get(e.getKey())).count(e.getValue()).build())
                .toList();
    }
}
