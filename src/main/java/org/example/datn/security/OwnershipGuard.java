package org.example.datn.security;

import org.example.datn.domain.Conversation;
import org.example.datn.domain.Order;
import org.example.datn.domain.Restaurant;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Layer-5 security: verifies a resource actually belongs to the caller, beyond
 * the coarse role check done by {@code @PreAuthorize}.
 */
@Component
public class OwnershipGuard {

    public void checkOrderOwner(Order order, Long userId) {
        if (!order.getCustomer().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    public void checkRestaurantOwner(Restaurant restaurant, Long userId) {
        if (!restaurant.getOwner().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    public void checkShipperAssigned(Order order, Long shipperId) {
        if (order.getShipper() == null
                || !order.getShipper().getUserId().equals(shipperId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    public void checkConversationMember(Conversation conversation, Long userId) {
        boolean member = conversation.getUser1().getUserId().equals(userId)
                || conversation.getUser2().getUserId().equals(userId);
        if (!member) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}
