package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.DTO.response.stats.UserStatsResponse;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.User;
import org.example.datn.domain.RestaurantRegister;
import org.example.datn.domain.ShipperRegister;
import org.example.datn.domain.Restaurant;
import org.example.datn.domain.Shipper;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.Repository.ShipperRepository;
import org.example.datn.domain.enums.Role;
import org.example.datn.domain.enums.VehicleType;
import org.example.datn.DTO.response.auth.UserResponse;
import org.example.datn.DTO.response.restaurant.RestaurantRegisterResponse;
import org.example.datn.DTO.response.auth.ShipperRegisterResponse;
import org.example.datn.DTO.request.auth.ReviewRegisterRequest;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.UserMapper;
import org.example.datn.Repository.UserRepository;
import org.example.datn.Repository.RestaurantRegisterRepository;
import org.example.datn.Repository.ShipperRegisterRepository;
import org.example.datn.Repository.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final RestaurantRegisterRepository restaurantRegisterRepository;
    private final ShipperRegisterRepository shipperRegisterRepository;
    private final RestaurantRepository restaurantRepository;
    private final ShipperRepository shipperRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(String role, Boolean activeStatus, Pageable pageable) {
        Page<User> userPage;
        boolean hasRole = StringUtils.hasText(role) && !"all".equalsIgnoreCase(role);
        boolean hasStatus = activeStatus != null;

        if (hasRole && hasStatus) {
            userPage = userRepository.findByRoleAndStatus(Role.valueOf(role.toUpperCase()), activeStatus, pageable);
        } else if (hasRole) {
            userPage = userRepository.findByRole(Role.valueOf(role.toUpperCase()), pageable);
        } else if (hasStatus) {
            userPage = userRepository.findByStatus(activeStatus, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        // Sử dụng UserMapper để map từng phần tử User sang UserResponse
        return userPage.map(userMapper::toResponse);
    }

    public UserStatsResponse getUserStats() {
        return UserStatsResponse.builder()
                .totalUser(userRepository.count())
                .activeUser(userRepository.countByStatusTrue())
                .blockedUser(userRepository.countByStatusFalse())
                .totalAdmin(userRepository.countByRole(Role.ADMIN))
                .totalCustomer(userRepository.countByRole(Role.CUSTOMER))
                .totalOwner(userRepository.countByRole(Role.OWNER))
                .totalShipper(userRepository.countByRole(Role.SHIPPER))
                .build();
    }

    @Transactional
    public UserResponse setUserStatus(Long userId, boolean active, String lockedReason) {
        User user = userRepository.findByIdOrThrow(userId, ErrorCode.USER_NOT_FOUND);
        user.setStatus(active);
        if (!active) {
            user.setLockedAt(LocalDateTime.now());
            user.setLockedReason(lockedReason != null && !lockedReason.trim().isEmpty()
                    ? lockedReason
                    : "Tài khoản bị khóa bởi quản trị viên");
        } else {
            user.setLockedAt(null);
            user.setLockedReason(null);
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantRegisterResponse> listRestaurantRegisters(RegisterStatus status, Pageable pageable) {
        Page<RestaurantRegister> page = (status == null)
                ? restaurantRegisterRepository.findAll(pageable)
                : restaurantRegisterRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(this::toRestaurantRegisterResponse));
    }

    @Transactional
    public RestaurantRegisterResponse reviewRestaurantRegister(Long adminId, Long registerId, ReviewRegisterRequest req) {
        RestaurantRegister register = restaurantRegisterRepository.findById(registerId)
                .orElseThrow(() -> new org.example.datn.Exception.AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu đăng ký"));

        if (register.getStatus() != RegisterStatus.PENDING) {
            throw new org.example.datn.Exception.AppException(ErrorCode.VALIDATION_FAILED, "Yêu cầu đăng ký đã được xử lý trước đó");
        }

        User admin = userRepository.getReferenceById(adminId);
        RegisterStatus status = RegisterStatus.valueOf(req.getStatus().toUpperCase());
        register.setStatus(status);
        register.setReviewedBy(admin);
        register.setReviewedAt(LocalDateTime.now());

        if (status == RegisterStatus.APPROVED) {
            register.setRejectedReason(null);

            // Kích hoạt tài khoản của Owner
            User owner = register.getOwner();
            owner.setStatus(true);
            userRepository.save(owner);

            // Tự động tạo bản ghi Restaurant
            Restaurant restaurant = Restaurant.builder()
                    .owner(owner)
                    .restaurantName(register.getRestaurantName())
                    .address(register.getAddress())
                    .latitude(register.getLatitude())
                    .longitude(register.getLongitude())
                    .phone(register.getPhone())
                    .description(register.getDescription())
                    .imageUrl(register.getImageUrl())
                    .status(true)
                    .build();
            restaurantRepository.save(restaurant);
        } else if (status == RegisterStatus.REJECTED) {
            register.setRejectedReason(req.getRejectedReason());
        }

        return toRestaurantRegisterResponse(restaurantRegisterRepository.save(register));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShipperRegisterResponse> listShipperRegisters(RegisterStatus status, Pageable pageable) {
        Page<ShipperRegister> page = (status == null)
                ? shipperRegisterRepository.findAll(pageable)
                : shipperRegisterRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(this::toShipperRegisterResponse));
    }

    @Transactional
    public ShipperRegisterResponse reviewShipperRegister(Long adminId, Long registerId, ReviewRegisterRequest req) {
        ShipperRegister register = shipperRegisterRepository.findById(registerId)
                .orElseThrow(() -> new org.example.datn.Exception.AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu đăng ký tài xế"));

        if (register.getStatus() != RegisterStatus.PENDING) {
            throw new org.example.datn.Exception.AppException(ErrorCode.VALIDATION_FAILED, "Yêu cầu đăng ký đã được xử lý trước đó");
        }

        User admin = userRepository.getReferenceById(adminId);
        RegisterStatus status = RegisterStatus.valueOf(req.getStatus().toUpperCase());
        register.setStatus(status);
        register.setReviewedBy(admin);
        register.setReviewedAt(LocalDateTime.now());

        if (status == RegisterStatus.APPROVED) {
            register.setRejectedReason(null);

            // Kích hoạt tài khoản của Shipper
            User shipper = register.getUser();
            shipper.setStatus(true);
            userRepository.save(shipper);

            // Tự động tạo bản ghi Shipper
            Shipper shipperEntity = Shipper.builder()
                    .user(shipper)
                    .idCard(register.getIdCard())
                    .vehicleType(register.getVehicleType())
                    .licensePlate(register.getLicensePlate())
                    .isOnline(false)
                    .avgRating(java.math.BigDecimal.ZERO)
                    .totalDelivery(0)
                    .activeDelivery(0)
                    .build();
            shipperRepository.save(shipperEntity);
        } else if (status == RegisterStatus.REJECTED) {
            register.setRejectedReason(req.getRejectedReason());
        }

        return toShipperRegisterResponse(shipperRegisterRepository.save(register));
    }

    private RestaurantRegisterResponse toRestaurantRegisterResponse(RestaurantRegister r) {
        return RestaurantRegisterResponse.builder()
                .registerId(r.getRegisterId())
                .ownerId(r.getOwner().getUserId())
                .ownerName(r.getOwner().getFullName())
                .restaurantName(r.getRestaurantName())
                .address(r.getAddress())
                .phone(r.getPhone())
                .imageUrl(r.getImageUrl())
                .description(r.getDescription())
                .status(r.getStatus())
                .rejectedReason(r.getRejectedReason())
                .reviewedById(r.getReviewedBy() != null ? r.getReviewedBy().getUserId() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private ShipperRegisterResponse toShipperRegisterResponse(ShipperRegister s) {
        Integer active = 0;
        Integer total = 0;
        if (s.getStatus() == RegisterStatus.APPROVED) {
            java.util.Optional<Shipper> shipperOpt = shipperRepository.findByUserUserId(s.getUser().getUserId());
            if (shipperOpt.isPresent()) {
                active = shipperOpt.get().getActiveDelivery();
                total = shipperOpt.get().getTotalDelivery();
            }
        }

        return ShipperRegisterResponse.builder()
                .registerId(s.getRegisterId())
                .userId(s.getUser().getUserId())
                .userName(s.getUser().getFullName())
                .email(s.getUser().getEmail())
                .phone(s.getUser().getPhone())
                .idCard(s.getIdCard())
                .vehicleType(s.getVehicleType())
                .licensePlate(s.getLicensePlate())
                .status(s.getStatus())
                .rejectedReason(s.getRejectedReason())
                .reviewedById(s.getReviewedBy() != null ? s.getReviewedBy().getUserId() : null)
                .reviewedByName(s.getReviewedBy() != null ? s.getReviewedBy().getFullName() : null)
                .reviewedAt(s.getReviewedAt())
                .createdAt(s.getCreatedAt())
                .activeDelivery(active)
                .totalDelivery(total)
                .build();
    }
}
