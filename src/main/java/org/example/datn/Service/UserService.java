package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.Exception.AppException;
import org.example.datn.domain.User;
import org.example.datn.domain.CustomerAddress;
import org.example.datn.domain.RestaurantRegister;
import org.example.datn.domain.ShipperRegister;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.UserMapper;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.ShipperRegisterRepository;
import org.example.datn.Repository.CustomerAddressRepository;
import org.example.datn.Repository.RestaurantRegisterRepository;
import org.example.datn.Repository.ShipperRepository;
import org.example.datn.domain.Shipper;
import org.example.datn.domain.enums.Role;
import org.example.datn.domain.enums.VehicleType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ShipperRegisterRepository shipperRegisterRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final RestaurantRegisterRepository restaurantRegisterRepository;
    private final ShipperRepository shipperRepository;
    private final ImageUploadService imageUploadService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        UserResponse response = userMapper.toResponse(user);
        if (user.getRole() == Role.SHIPPER) {
            shipperRepository.findByUserUserId(userId).ifPresent(reg -> {
                response.setVehicleType(reg.getVehicleType() != null ? reg.getVehicleType().name() : null);
                response.setLicensePlate(reg.getLicensePlate());
                response.setIdCard(reg.getIdCard());
            });
            shipperRepository.findByUserUserId(userId).ifPresent(shipper -> {
                response.setActiveDelivery(shipper.getActiveDelivery());
                response.setTotalDelivery(shipper.getTotalDelivery());
            });
        }
        return response;
    }

    @Transactional
    public UserResponse updateProfile(Long userId, org.example.datn.DTO.request.auth.UpdateProfileRequest req) {
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        if (req.getFullName() != null && !req.getFullName().trim().isEmpty()) {
            user.setFullName(req.getFullName().trim());
        }
        if (req.getPhone() != null && !req.getPhone().trim().isEmpty()) {
            user.setPhone(req.getPhone().trim());
        }
        if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
            String newEmail = req.getEmail().trim();
            if (!newEmail.equals(user.getEmail())) {
                boolean emailExists = userRepository.existsByEmail(newEmail);
                if (emailExists) {
                    throw new AppException(ErrorCode.EMAIL_EXISTS, "Email đã tồn tại!");
                }
                user.setEmail(newEmail);
            }
        }
        if (req.getAvatar() != null) {
            String oldAvatar = user.getAvatar();
            String newAvatar = req.getAvatar().trim();
            if (!newAvatar.equals(oldAvatar)) {
                if (oldAvatar != null && !oldAvatar.trim().isEmpty()) {
                    imageUploadService.deleteImage(oldAvatar);
                }
                user.setAvatar(newAvatar);
            }
        }

        if (user.getRole() == Role.CUSTOMER) {
            CustomerAddress address = customerAddressRepository.findByCustomerUserIdAndIsDefaultTrue(userId)
                    .orElseGet(() -> {
                        List<CustomerAddress> all = customerAddressRepository.findByCustomerUserId(userId);
                        return all.isEmpty() ? null : all.get(0);
                    });

            if (address == null) {
                address = new CustomerAddress();
                address.setCustomer(user);
                address.setIsDefault(true);
            }
            if (req.getAddress() != null) {
                address.setAddress(req.getAddress().trim());
            }
            if (req.getLatitude() != null) {
                address.setLatitude(java.math.BigDecimal.valueOf(req.getLatitude()));
            }
            if (req.getLongitude() != null) {
                address.setLongitude(java.math.BigDecimal.valueOf(req.getLongitude()));
            }
            customerAddressRepository.save(address);
        }
        userRepository.save(user);

        if (user.getRole() == Role.SHIPPER) {
            shipperRepository.findByUserUserId(userId).ifPresent(shipper -> {
                if (req.getLicensePlate() != null && !req.getLicensePlate().trim().isEmpty()) {
                    String newLicensePlate = req.getLicensePlate().trim();
                    if (!newLicensePlate.equals(shipper.getLicensePlate())) {
                        boolean plateExists = shipperRepository.existsByLicensePlate(newLicensePlate);
                        if (plateExists) {
                            throw new AppException(ErrorCode.LICENSE_PLATE_EXISTS);
                        }
                        shipper.setLicensePlate(req.getLicensePlate().trim());
                    }
                }
                if (req.getVehicleType() != null && !req.getVehicleType().trim().isEmpty()) {
                    try {
                        shipper.setVehicleType(VehicleType.valueOf(req.getVehicleType().trim().toUpperCase()));
                    } catch (Exception e) {
                        // ignore invalid vehicle type
                    }
                }

                if (req.getIsOnline() != null) {
                    shipper.setIsOnline(req.getIsOnline());
                    if (req.getIsOnline()) {
                        shipper.setLastOnlineAt(java.time.LocalDateTime.now());
                    }
                }

                shipperRepository.save(shipper);
            });
        }

        UserResponse response = userMapper.toResponse(user);
        if (user.getRole() == Role.SHIPPER) {
            shipperRepository.findByUserUserId(userId).ifPresent(shipper -> {
                response.setVehicleType(shipper.getVehicleType() != null ? shipper.getVehicleType().name() : null);
                response.setLicensePlate(shipper.getLicensePlate());
                response.setActiveDelivery(shipper.getActiveDelivery());
                response.setTotalDelivery(shipper.getTotalDelivery());
                response.setIsOnline(shipper.getIsOnline());
            });
        }

        return response;
    }

    @Transactional
    public UserResponse ownerReRegister(Long userId, org.example.datn.DTO.request.auth.OwnerReRegisterRequest req) {
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        RestaurantRegister reg = restaurantRegisterRepository.findTopByOwnerUserIdOrderByRegisterIdDesc(userId)
                .orElseGet(() -> RestaurantRegister.builder().owner(user).build());

        reg.setRestaurantName(req.getRestaurantName().trim());
        reg.setAddress(req.getRestaurantAddress().trim());
        reg.setLatitude(req.getRestaurantLatitude());
        reg.setLongitude(req.getRestaurantLongitude());
        reg.setPhone(req.getRestaurantPhone().trim());
        reg.setDescription(req.getRestaurantDescription() != null ? req.getRestaurantDescription().trim() : null);
        reg.setImageUrl(req.getRestaurantImageUrl() != null ? req.getRestaurantImageUrl().trim() : null);
        reg.setStatus(RegisterStatus.PENDING);
        reg.setRejectedReason(null);
        reg.setReviewedAt(null);
        restaurantRegisterRepository.save(reg);

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse shipperReRegister(Long userId, org.example.datn.DTO.request.auth.ShipperReRegisterRequest req) {
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        ShipperRegister reg = shipperRegisterRepository.findTopByUserUserIdOrderByRegisterIdDesc(userId)
                .orElseGet(() -> ShipperRegister.builder().user(user).build());

        VehicleType vehicleType = VehicleType.MOTORBIKE;
        try {
            vehicleType = VehicleType.valueOf(req.getVehicleType().trim().toUpperCase());
        } catch (Exception e) {
            // ignore, default MOTORBIKE
        }

        reg.setIdCard(req.getIdCard().trim());
        reg.setVehicleType(vehicleType);
        reg.setLicensePlate(req.getLicensePlate().trim());
        reg.setStatus(RegisterStatus.PENDING);
        reg.setRejectedReason(null);
        reg.setReviewedAt(null);
        shipperRegisterRepository.save(reg);

        UserResponse response = userMapper.toResponse(user);
        response.setVehicleType(reg.getVehicleType().name());
        response.setLicensePlate(reg.getLicensePlate());
        response.setIdCard(reg.getIdCard());
        return response;
    }
}