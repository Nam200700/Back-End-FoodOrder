package org.example.datn.DTO.response.voucher;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherStatsResponse {
    private long totalVouchers;
    private long activeVouchers;
    private long inactiveVouchers;
}