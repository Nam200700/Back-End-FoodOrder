package org.example.datn.Service.external;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Service.PaymentService;
import org.example.datn.domain.Order;
import org.example.datn.util.VNPayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayService {

    private final PaymentService paymentService;

    @Value("${app.vnpay.tmn-code}")
    private String tmnCode;
    @Value("${app.vnpay.hash-secret}")
    private String hashSecret;
    @Value("${app.vnpay.url}")
    private String vnpUrl;
    @Value("${app.vnpay.return-url}")
    private String returnUrl;

    /** Builds the redirect URL that sends the customer to VNPay. */
    public String createPaymentUrl(Order order, HttpServletRequest request) {
        long amount = order.getTotalAmount().longValueExact() * 100; // VNPay uses VND x100

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", String.valueOf(order.getOrderId()));
        params.put("vnp_OrderInfo", "Thanh toan don hang " + order.getOrderId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", VNPayUtils.getClientIp(request));
        params.put("vnp_CreateDate", VNPayUtils.now());

        String data = VNPayUtils.buildEncodedData(params);
        String secureHash = VNPayUtils.hmacSHA512(hashSecret, data);
        return vnpUrl + "?" + data + "&vnp_SecureHash=" + secureHash;
    }

    /**
     * Verifies the signature of an IPN/return callback and applies the result.
     *
     * @return true when the payment succeeded (response code "00").
     */
    public boolean handleCallback(Map<String, String> allParams) {
        Map<String, String> params = new HashMap<>(allParams);
        String receivedHash = params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String data = VNPayUtils.buildEncodedData(new TreeMap<>(params));
        String computedHash = VNPayUtils.hmacSHA512(hashSecret, data);

        if (receivedHash == null || !receivedHash.equalsIgnoreCase(computedHash)) {
            throw new AppException(ErrorCode.VNPAY_INVALID_SIGNATURE);
        }

        Long orderId = Long.parseLong(params.get("vnp_TxnRef"));
        boolean success = "00".equals(params.get("vnp_ResponseCode"));
        paymentService.applyVnpayResult(orderId, success, params.get("vnp_TransactionNo"));
        log.info("VNPay callback for order {} success={}", orderId, success);
        return success;
    }
}
