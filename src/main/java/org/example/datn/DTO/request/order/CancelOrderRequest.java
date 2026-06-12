package org.example.datn.DTO.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelOrderRequest {
    @NotBlank(message = "Vui lòng nhập lý do hủy")
    @Size(max = 300, message = "Lý do hủy tối đa 300 ký tự")
    private String reason;
}
