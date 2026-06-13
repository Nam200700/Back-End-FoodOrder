package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.User;
import org.example.datn.DTO.request.restaurant.CreateRestaurantRequest;
import org.example.datn.DTO.response.restaurant.RestaurantResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.RestaurantMapper;
import org.example.datn.Repository.RestaurantRepository;
import org.example.datn.Repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;
    private final ImageUploadService imageUploadService;

    @Transactional(readOnly = true)
    public Page<RestaurantResponse> listActive(Pageable pageable) {
        return restaurantRepository.findByStatusTrue(pageable).map(restaurantMapper::toResponse);
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
        restaurant.setRestaurantName(req.getRestaurantName());
        restaurant.setAddress(req.getAddress());
        restaurant.setLatitude(req.getLatitude());
        restaurant.setLongitude(req.getLongitude());
        restaurant.setPhone(req.getPhone());
        restaurant.setDescription(req.getDescription());
        if (req.getImageUrl() != null && !req.getImageUrl().isBlank()) {
            String oldImageUrl = restaurant.getImageUrl();
            String newImageUrl = req.getImageUrl().trim();
            if (!newImageUrl.equals(oldImageUrl)) {
                if (oldImageUrl != null && !oldImageUrl.trim().isEmpty()) {
                    imageUploadService.deleteImage(oldImageUrl);
                }
                restaurant.setImageUrl(newImageUrl);
            }
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
}
