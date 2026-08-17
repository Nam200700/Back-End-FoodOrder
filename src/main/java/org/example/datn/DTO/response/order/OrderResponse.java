package org.example.datn.DTO.response.order;

import lombok.Builder;
import lombok.Data;
import org.example.datn.domain.enums.OrderStatus;
import org.example.datn.domain.enums.PaymentMethod;
import org.example.datn.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long orderId;

    private Long customerId;
    private String customerName;
    private String customerPhone;

    private Long restaurantId;
    private String restaurantName;
    private String restaurantAddress;

    private Long shipperId;
    private String shipperName;
    private String shipperPhone;
    private String shipperAvatar;
    private String shipperVehicleType;
    private String shipperLicensePlate;

    private BigDecimal subtotalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    private String voucherCode;

    private String deliveryAddress;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;

    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private OrderStatus orderStatus;

    private String note;
    private String cancelReason;

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;

    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime pickedUpAt;

    private Boolean reviewed;
    private Integer restaurantRating;
    private Integer shipperRating;

    private List<OrderItemResponse> items;

    // Thông tin đơn đặt nhóm — null nếu là đơn cá nhân
    private Long groupOrderId;
    private Long groupHostId;
    private String groupHostName;
}
