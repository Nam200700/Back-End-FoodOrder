package org.example.datn.DTO.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.datn.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateBulkOrderRequest {

    @NotBlank(message = "Địa chỉ giao hàng không được để trống!")
    private String deliveryAddress;

    @NotEmpty(message = "Id quán ăn không được để trống")
    private List<Long> restaurantIds;

    private BigDecimal deliveryLat;

    private BigDecimal deliveryLng;

    @NotNull(message = "Phương thức thanh toán không được để trống!")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Phí giao hàng không được để trông!")
    private BigDecimal shippingFee;

    private String note;
}
