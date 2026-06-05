package org.example.datn.Exception;

/**
 * Raised when a review cannot be created (order not completed, past the review
 * window, or already reviewed). Mapped to HTTP 422.
 */
public class ReviewNotAllowedException extends RuntimeException {

    public ReviewNotAllowedException(String message) {
        super(message);
    }
}
