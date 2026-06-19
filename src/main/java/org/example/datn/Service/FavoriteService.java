package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.FavoriteRestaurant;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.RestaurantMapper;
import org.example.datn.Repository.FavoriteRestaurantRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRestaurantRepository favoriteRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;

    @Transactional(readOnly = true)
    public List<RestaurantResponse> list(Long customerId) {
        return favoriteRepository.findByCustomerUserId(customerId).stream()
                .map(f -> restaurantMapper.toResponse(f.getRestaurant()))
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
