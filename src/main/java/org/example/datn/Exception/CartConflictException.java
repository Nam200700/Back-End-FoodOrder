package org.example.datn.Exception;

import lombok.Getter;

/**
 * Raised when a customer adds an item from a different restaurant than the one
 * already in their cart. Mapped to HTTP 409 with {@code requiresReplace} so the
 * FE can show a "replace cart" confirmation modal.
 */
@Getter
public class CartConflictException extends RuntimeException {

    private final String currentRestaurantName;

    public CartConflictException(String message, String currentRestaurantName) {
        super(message);
        this.currentRestaurantName = currentRestaurantName;
    }
}
