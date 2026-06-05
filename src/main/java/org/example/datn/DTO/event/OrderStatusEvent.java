package org.example.datn.DTO.event;

import org.example.datn.domain.Order;
import org.example.datn.domain.enums.OrderStatus;

import java.time.LocalDateTime;

/**
 * Broadcast on {@code /topic/order/{orderId}} after every status change.
 */
public record OrderStatusEvent(Long orderId, OrderStatus newStatus, LocalDateTime timestamp) {

    public static OrderStatusEvent from(Order order) {
        return new OrderStatusEvent(order.getOrderId(), order.getOrderStatus(), LocalDateTime.now());
    }
}
