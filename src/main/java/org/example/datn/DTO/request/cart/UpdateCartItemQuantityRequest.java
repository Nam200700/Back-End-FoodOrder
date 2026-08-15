package org.example.datn.DTO.request.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCartItemQuantityRequest {

    @NotNull(message = "quantity không được để trống")
    @Min(value = 0, message = "quantity không được nhỏ hơn 0")
    private Integer quantity;
}
