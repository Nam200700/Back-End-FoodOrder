package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Order;
import org.example.datn.DTO.event.OrderStatusEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper over STOMP messaging for order-related broadcasts.
 */
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Notify everyone subscribed to {@code /topic/order/{id}}. */
    public void broadcastOrderStatus(Order order) {
        messagingTemplate.convertAndSend(
                "/topic/order/" + order.getOrderId(),
                OrderStatusEvent.from(order));
    }

    /** Notify shippers watching the available-orders feed. */
    public void broadcastAvailableOrder(Order order) {
        messagingTemplate.convertAndSend(
                "/topic/available-orders",
                OrderStatusEvent.from(order));
    }
}
