package org.example.datn.DTO.response.voucher;

import lombok.*;
import org.example.datn.domain.enums.DiscountType;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.domain.enums.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherResponse {
    private Long voucherId;
    private String code;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private Integer usedQuantity;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private VoucherStatus status;
    private VoucherIssueType issueType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}