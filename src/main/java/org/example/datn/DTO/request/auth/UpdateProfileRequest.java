package org.example.datn.DTO.request.auth;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String phone;
    private String address;
    private Double latitude;
    private Double longitude;
    private String vehicleType;
    private String licensePlate;
    private String avatar;
}
