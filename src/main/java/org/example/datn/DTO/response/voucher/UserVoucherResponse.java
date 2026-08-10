package org.example.datn.DTO.response.voucher;

import lombok.*;
import org.example.datn.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVoucherResponse {
    private Long userVoucherId;
    private Long voucherId;
    private String code;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private LocalDateTime receivedAt;
    private LocalDateTime expiredAt;
    private Boolean used;
    private LocalDateTime usedAt;
}
