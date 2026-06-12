package org.example.datn.DTO.response.cart;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long cartItemId;
    private Long foodId;
    private String foodName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal lineTotal;
    private String note;
    private String foodImageUrl;
}
