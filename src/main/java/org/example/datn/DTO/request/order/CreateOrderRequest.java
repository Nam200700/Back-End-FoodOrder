package org.example.datn.DTO.request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.datn.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CreateOrderRequest {

    @NotNull(message = "Vui lòng chọn địa chỉ giao hàng")
    private Long addressId;

    @NotNull(message = "ID nhà hàng không được để trống")
    private List<Long> restaurantId;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    private String note;

    private Map<Long, Long> restaurantVouchers;
}