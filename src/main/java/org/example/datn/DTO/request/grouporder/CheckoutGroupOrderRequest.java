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

    /** Hệ thống hiện chỉ hỗ trợ COD (xem enum PaymentMethod) nên mặc định luôn là COD. */
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    /** Voucher của HOST áp cho cả đơn gộp (tùy chọn). */
    private Long userVoucherId;

    private String note;
}
