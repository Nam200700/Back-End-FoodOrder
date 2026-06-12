package org.example.datn.Controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.datn.Service.PaymentService;
import org.example.datn.Service.external.VNPayService;
import org.example.datn.common.ApiResponse;
import org.example.datn.domain.Order;
import org.example.datn.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final VNPayService vnPayService;
    private final PaymentService paymentService;

    /** Customer requests a VNPay redirect URL for one of their orders. */
    @PostMapping("/vnpay/create-url")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> createUrl(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long orderId,
            HttpServletRequest request) {
        paymentService.assertCustomerOwnsOrder(orderId, user.getUserId());
        Order order = paymentService.requireOrder(orderId);
        String url = vnPayService.createPaymentUrl(order, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("paymentUrl", url)));
    }

    /** Server-to-server IPN from VNPay (public). Returns the VNPay-expected ack. */
    @GetMapping("/vnpay/ipn")
    public ResponseEntity<Map<String, String>> ipn(@RequestParam Map<String, String> params) {
        try {
            vnPayService.handleCallback(params);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid signature"));
        }
    }

    /** Browser return URL (public). FE reads success flag to render the result. */
    @GetMapping("/vnpay/return")
    public ResponseEntity<ApiResponse<Map<String, Object>>> vnpayReturn(
            @RequestParam Map<String, String> params) {
        boolean success = vnPayService.handleCallback(params);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "success", success,
                "orderId", params.getOrDefault("vnp_TxnRef", ""))));
    }
}
