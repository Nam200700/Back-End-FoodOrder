package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.FavoriteRestaurant;
import org.example.datn.domain.Restaurant;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.RestaurantMapper;
import org.example.datn.Repository.FavoriteRestaurantRepository;
import org.example.datn.Repository.OrderRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.ReviewRepository;
import org.example.datn.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRestaurantRepository favoriteRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<RestaurantResponse> list(Long customerId) {
        List<Restaurant> favorites = favoriteRepository.findByCustomerUserId(customerId).stream()
                .map(FavoriteRestaurant::getRestaurant)
                .toList();
        if (favorites.isEmpty()) {
            return List.of();
        }

        // Gộp rating/số review/số đơn hoàn tất bằng 2 câu GROUP BY ... IN(ids) thay vì 3 query MỖI quán → khử N+1.
        List<Long> ids = favorites.stream().map(Restaurant::getRestaurantId).toList();

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

        return favorites.stream()
                .map(r -> restaurantMapper.toResponse(
                        r,
                        avgMap.get(r.getRestaurantId()),
                        reviewCountMap.getOrDefault(r.getRestaurantId(), 0L),
                        orderCountMap.getOrDefault(r.getRestaurantId(), 0L)))
                .toList();
    }

    @Transactional
    public void add(Long customerId, Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new org.example.datn.Exception.AppException(ErrorCode.RESTAURANT_NOT_FOUND);
        }
        if (favoriteRepository.existsByCustomerUserIdAndRestaurantRestaurantId(customerId, restaurantId)) {
            return; // idempotent
        }
        favoriteRepository.save(FavoriteRestaurant.builder()
                .customer(userRepository.getReferenceById(customerId))
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .build());
    }

    @Transactional
    public void remove(Long customerId, Long restaurantId) {
        favoriteRepository.deleteByCustomerUserIdAndRestaurantRestaurantId(customerId, restaurantId);
    }
}
