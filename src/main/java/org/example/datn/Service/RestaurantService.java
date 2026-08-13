package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.Repository.*;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.User;
import org.example.datn.DTO.request.restaurant.CreateRestaurantRequest;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.mapper.RestaurantMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;
    private final ImageUploadService imageUploadService;

    @Transactional(readOnly = true)
    public Page<RestaurantResponse> listActive(Pageable pageable) {
        return enrichPage(restaurantRepository.findByStatusTrue(pageable));
    }

    /**
     * Danh sách quán đang mở có LỌC theo từ khoá (tên/địa chỉ) — dùng cho feed + tìm kiếm trang Khám phá.
     * CHỈ cache feed KHÔNG từ khoá (trang chủ/Khám phá cuộn) vì nó CHUNG cho mọi khách và đổi chậm
     * (khoảng cách được xếp ở client). Tìm kiếm theo từ khoá không cache (muôn hình vạn trạng).
     */
    @Cacheable(value = "restaurantFeed",
            key = "#pageable.pageNumber + '-' + #pageable.pageSize",
            condition = "#keyword == null || #keyword.isBlank()")
    @Transactional(readOnly = true)
    public Page<RestaurantResponse> listActive(String keyword, Pageable pageable) {
        Page<Restaurant> page = (keyword == null || keyword.isBlank())
                ? restaurantRepository.findByStatusTrue(pageable)
                : restaurantRepository.searchActive(keyword.trim(), pageable);
        return enrichPage(page);
    }

    /**
     * Map cả TRANG quán với rating/số review/số đơn hoàn tất được gộp bằng 2 câu
     * GROUP BY ... IN(ids) thay vì 3 query MỖI quán → khử N+1 (trang chủ, Khám phá).
     */
    private Page<RestaurantResponse> enrichPage(Page<Restaurant> page) {
        List<Restaurant> list = page.getContent();
        if (list.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }

        List<Long> ids = list.stream().map(Restaurant::getRestaurantId).toList();

        Map<Long, Double> avgMap = new HashMap<>();
        Map<Long, Long> reviewCountMap = new HashMap<>();
        for (Object[] row : reviewRepository.aggregateRatingByRestaurantIds(ids)) {
            Long rid = (Long) row[0];
            avgMap.put(rid, row[1] != null ? ((Number) row[1]).doubleValue() : null);
            reviewCountMap.put(rid, ((Number) row[2]).longValue());
        }

        Map<Long, Long> orderCountMap = new HashMap<>();
        for (Object[] row : orderRepository.countCompletedByRestaurantIds(ids)) {
            orderCountMap.put((Long) row[0], ((Number) row[1]).longValue());
        }

        List<RestaurantResponse> content = list.stream()
                .map(r -> restaurantMapper.toResponse(
                        r,
                        avgMap.get(r.getRestaurantId()),
                        reviewCountMap.getOrDefault(r.getRestaurantId(), 0L),
                        orderCountMap.getOrDefault(r.getRestaurantId(), 0L)))
                .toList();

        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getById(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse create(Long ownerId, CreateRestaurantRequest req) {
        User owner = userRepository.findByIdOrThrow(ownerId, ErrorCode.USER_NOT_FOUND);
        Restaurant restaurant = Restaurant.builder()
                .owner(owner)
                .restaurantName(req.getRestaurantName())
                .address(req.getAddress())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .phone(req.getPhone())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .status(true)
                .build();
        return restaurantMapper.toResponse(restaurantRepository.save(restaurant));
    }

    @Transactional
    public RestaurantResponse update(Long ownerId, Long restaurantId, CreateRestaurantRequest req) {
        Restaurant restaurant = restaurantRepository.findByIdOrThrow(restaurantId, ErrorCode.RESTAURANT_NOT_FOUND);
        if (!restaurant.getOwner().getUserId().equals(ownerId)) {
            throw new org.example.datn.Exception.AppException(ErrorCode.FORBIDDEN);
        }
        // Bắt ảnh CŨ TRƯỚC khi ghi đè — nếu không, oldImageUrl == newImageUrl và ảnh cũ không bao giờ bị xoá (rò rỉ Cloudinary).
        String oldImageUrl = restaurant.getImageUrl();
        restaurant.setRestaurantName(req.getRestaurantName());
        restaurant.setAddress(req.getAddress());
        restaurant.setLatitude(req.getLatitude());
        restaurant.setLongitude(req.getLongitude());
        restaurant.setPhone(req.getPhone());
        restaurant.setDescription(req.getDescription());
        restaurant.setImageUrl(req.getImageUrl());
        restaurant.setClosesAt(req.getClosesAt());
        restaurant.setOpensAt(req.getOpensAt());
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            String newImageUrl = req.getImageUrl().trim();
            if (!newImageUrl.equals(oldImageUrl) && oldImageUrl != null && !oldImageUrl.trim().isEmpty()) {
                imageUploadService.deleteImage(oldImageUrl);
            }
            restaurant.setImageUrl(newImageUrl);
        }
        return restaurantMapper.toResponse(restaurantRepository.save(restaurant));
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getByOwnerId(Long ownerId) {
        Restaurant restaurant = restaurantRepository.findByOwnerUserId(ownerId)
                .stream().findFirst()
                .orElseThrow(() -> new org.example.datn.Exception.AppException(ErrorCode.RESTAURANT_NOT_FOUND));
        return restaurantMapper.toResponse(restaurant);
    }

    @Transactional
    public RestaurantResponse updateStatus(Long ownerId, String statusDetailStr) {
        Restaurant restaurant = restaurantRepository.findByOwnerUserId(ownerId)
                .stream().findFirst()
                .orElseThrow(() -> new org.example.datn.Exception.AppException(ErrorCode.RESTAURANT_NOT_FOUND));
        
        try {
            org.example.datn.domain.enums.StatusDetail detail = org.example.datn.domain.enums.StatusDetail.valueOf(statusDetailStr.toUpperCase());
            if (detail == org.example.datn.domain.enums.StatusDetail.ACTIVE || detail == org.example.datn.domain.enums.StatusDetail.SELF_CLOSED) {
                restaurant.setStatusDetail(detail);
                restaurant.setStatus(detail == org.example.datn.domain.enums.StatusDetail.ACTIVE);
            } else {
                throw new org.example.datn.Exception.AppException(ErrorCode.VALIDATION_FAILED, "Trạng thái không hợp lệ");
            }
        } catch (IllegalArgumentException e) {
            throw new org.example.datn.Exception.AppException(ErrorCode.VALIDATION_FAILED, "Trạng thái không hợp lệ");
        }
        
        return restaurantMapper.toResponse(restaurantRepository.save(restaurant));
    }

    @Transactional(readOnly = true)
    public Page<RestaurantResponse> getOrderAgain(Long customerId, Pageable pageable) {
        Page<Restaurant> page = orderRepository.findOrderAgainRestaurants(
                customerId,
                OrderStatus.COMPLETED,
                pageable
        );
        return enrichPage(page);
    }
}
