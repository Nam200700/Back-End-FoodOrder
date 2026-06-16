package org.example.datn.DTO.request.cart;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCartItemNoteRequest {

    @NotNull(message = "foodId không được để trống")
    private Long foodId;
    private String note;
}
