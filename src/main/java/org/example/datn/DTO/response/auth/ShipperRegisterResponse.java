package org.example.datn.DTO.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.datn.domain.enums.RegisterStatus;
import org.example.datn.domain.enums.VehicleType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipperRegisterResponse {
    private Long registerId;
    private Long userId;
    private String userName;
    private String email;
    private String phone;
    private String idCard;
    private VehicleType vehicleType;
    private String licensePlate;
    private RegisterStatus status;
    private String rejectedReason;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private Integer activeDelivery;
    private Integer totalDelivery;
}
