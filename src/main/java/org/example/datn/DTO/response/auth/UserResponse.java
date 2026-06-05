package org.example.datn.DTO.response.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String role;
    private String avatar;
    private Boolean status;
    private String vehicleType;
    private String licensePlate;
    private String idCard;
    private String address;
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private String registerStatus;
    private String rejectedReason;
    
    // Restaurant registration fields
    private String restaurantName;
    private String restaurantPhone;
    private String restaurantAddress;
    private String restaurantDescription;
    private String restaurantImageUrl;

    private java.time.LocalDateTime lockedAt;
    private String lockedReason;
    private Integer activeDelivery;
    private Integer totalDelivery;
    private java.math.BigDecimal avgRating;
}


