package org.example.datn.DTO.request.grouporder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.example.datn.domain.enums.PaymentMethod;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutGroupOrderRequest {

    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    /** Voucher của HOST áp cho cả đơn gộp (tùy chọn). */
    private Long userVoucherId;

    private String note;

    @Builder.Default
    private boolean force = false;
}
