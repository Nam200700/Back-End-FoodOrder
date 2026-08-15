package org.example.datn.DTO.response.grouporder;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupOrderItemResponse {
    private Long groupOrderItemId;
    private Long memberId;
    private String memberName;
    private Long foodId;
    private String foodName;
    private String foodImageUrl;
    private Integer quantity;
    private BigDecimal priceAtAdd;
    private BigDecimal lineTotal;
    private String note;
}
