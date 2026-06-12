package org.example.datn.DTO.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.datn.domain.enums.PaymentMethod;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    private String deliveryAddress;

    @NotNull(message = "ID nhà hàng không được để trống")
    private Long restaurantId;

    private BigDecimal deliveryLat;

    private BigDecimal deliveryLng;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Phí ship không được để trống")
    private BigDecimal shippingFee;

    private String note;
}
