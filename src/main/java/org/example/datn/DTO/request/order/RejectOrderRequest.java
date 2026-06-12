package org.example.datn.DTO.request.order;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectOrderRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String cancelReason;
}
