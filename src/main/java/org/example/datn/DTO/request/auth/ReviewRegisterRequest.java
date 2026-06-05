package org.example.datn.DTO.request.auth;

import lombok.Data;

@Data
public class ReviewRegisterRequest {
    private String status; // APPROVED, REJECTED
    private String rejectedReason;
}
