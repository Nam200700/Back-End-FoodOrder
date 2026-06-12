package org.example.datn.mapper;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.User;
import org.example.datn.domain.CustomerAddress;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.Repository.RestaurantRegisterRepository;
import org.example.datn.Repository.ShipperRegisterRepository;
import org.example.datn.domain.enums.Role;
import org.example.datn.domain.enums.RegisterStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final RestaurantRegisterRepository restaurantRegisterRepository;
    private final ShipperRegisterRepository shipperRegisterRepository;
    private final org.example.datn.Repository.ShipperRepository shipperRepository;

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }
        
        CustomerAddress defaultAddress = user.getAddresses() != null ? user.getAddresses().stream()
                .filter(addr -> addr.getIsDefault() != null && addr.getIsDefault())
                .findFirst()
                .orElse(user.getAddresses().isEmpty() ? null : user.getAddresses().get(0))
                : null;

        String registerStatus = "APPROVED";
        String rejectedReason = null;
        String restaurantName = null;
        String restaurantPhone = null;
        String restaurantAddress = null;
        String restaurantDescription = null;
        String restaurantImageUrl = null;

        String vehicleType = null;
        String licensePlate = null;
        String idCard = null;
        Integer activeDelivery = null;
        Integer totalDelivery = null;
        java.math.BigDecimal avgRating = null;

        if (user.getRole() == Role.OWNER) {
            var regOpt = restaurantRegisterRepository.findTopByOwnerUserIdOrderByRegisterIdDesc(user.getUserId());
            if (regOpt.isPresent()) {
                var reg = regOpt.get();
                registerStatus = reg.getStatus().name();
                rejectedReason = reg.getRejectedReason();
                restaurantName = reg.getRestaurantName();
                restaurantPhone = reg.getPhone();
                restaurantAddress = reg.getAddress();
                restaurantDescription = reg.getDescription();
                restaurantImageUrl = reg.getImageUrl();
            }
        } else if (user.getRole() == Role.SHIPPER) {
            var regOpt = shipperRegisterRepository.findTopByUserUserIdOrderByRegisterIdDesc(user.getUserId());
            if (regOpt.isPresent()) {
                var reg = regOpt.get();
                registerStatus = reg.getStatus().name();
                rejectedReason = reg.getRejectedReason();
                vehicleType = reg.getVehicleType() != null ? reg.getVehicleType().name() : null;
                licensePlate = reg.getLicensePlate();
                idCard = reg.getIdCard();
            }
            var shipperOpt = shipperRepository.findByUserUserId(user.getUserId());
            if (shipperOpt.isPresent()) {
                var shipper = shipperOpt.get();
                vehicleType = shipper.getVehicleType() != null ? shipper.getVehicleType().name() : vehicleType;
                licensePlate = shipper.getLicensePlate() != null ? shipper.getLicensePlate() : licensePlate;
                idCard = shipper.getIdCard() != null ? shipper.getIdCard() : idCard;
                activeDelivery = shipper.getActiveDelivery();
                totalDelivery = shipper.getTotalDelivery();
                avgRating = shipper.getAvgRating();
            }
        }

        return UserResponse.builder()
                .id(user.getUserId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .avatar(user.getAvatar())
                .status(user.getStatus()) // Map thủ công an toàn 100% không phụ thuộc MapStruct sinh code
                .address(defaultAddress != null ? defaultAddress.getAddress() : null)
                .latitude(defaultAddress != null ? defaultAddress.getLatitude() : null)
                .longitude(defaultAddress != null ? defaultAddress.getLongitude() : null)
                .registerStatus(registerStatus)
                .rejectedReason(rejectedReason)
                .restaurantName(restaurantName)
                .restaurantPhone(restaurantPhone)
                .restaurantAddress(restaurantAddress)
                .restaurantDescription(restaurantDescription)
                .restaurantImageUrl(restaurantImageUrl)
                .lockedAt(user.getLockedAt())
                .lockedReason(user.getLockedReason())
                .vehicleType(vehicleType)
                .licensePlate(licensePlate)
                .idCard(idCard)
                .activeDelivery(activeDelivery)
                .totalDelivery(totalDelivery)
                .avgRating(avgRating)
                .build();
    }
}
