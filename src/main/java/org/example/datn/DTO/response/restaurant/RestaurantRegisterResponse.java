package org.example.datn.DTO.response.restaurant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.datn.domain.enums.RegisterStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantRegisterResponse {
    private Long registerId;
    private Long ownerId;
    private String ownerName;
    private String restaurantName;
    private String address;
    private String phone;
    private String imageUrl;
    private String description;
    private RegisterStatus status;
    private String rejectedReason;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
