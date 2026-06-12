package org.example.datn.DTO.response.order;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {
    private Long orderItemId;
    private Long foodId;
    private String foodName;
    private Integer quantity;
    private BigDecimal priceAtOrder;
    private String note;
}
