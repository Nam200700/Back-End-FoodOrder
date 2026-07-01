package org.example.datn.Exception;

import lombok.Getter;

/**
 Khi user mà nhập đúng thì sđt và mk thì sẽ trả như dưới còn kẻ lẹ mặt mà sai
 thì sẽ ko trả như vậy tránh bị lộ tài khoản
 */
@Getter
public class EmailNotVerifiedException extends RuntimeException {

    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Tài khoản chưa xác thực OTP/email");
        this.email = email;
    }
}
