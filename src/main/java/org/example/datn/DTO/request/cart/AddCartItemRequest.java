package org.example.datn.DTO.request.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddCartItemRequest {

    @NotNull(message = "foodId không được để trống")
    private Long foodId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    private Integer quantity;

    private String note;
}
