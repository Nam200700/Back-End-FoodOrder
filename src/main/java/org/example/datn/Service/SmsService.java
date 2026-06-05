package org.example.datn.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Mock SMS gateway for the DATN scope. Swap with ESMS/Twilio in production.
 * Never log OTP codes in a real deployment.
 */
@Slf4j
@Service
public class SmsService {

    public void sendOtp(String phone, String code) {
        log.info("[MOCK SMS] OTP for {} -> {}", phone, code);
    }
}
