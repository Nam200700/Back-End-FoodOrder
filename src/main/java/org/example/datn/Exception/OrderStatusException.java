package org.example.datn.Exception;

/**
 * Raised on an illegal order status transition. Mapped to HTTP 422.
 */
public class OrderStatusException extends RuntimeException {

    public OrderStatusException(String message) {
        super(message);
    }
}
