package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.datn.Exception.AppException;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.Repository.OrderRepository;
import org.example.datn.Repository.PaymentRepository;
import org.example.datn.domain.Order;
import org.example.datn.domain.Payment;
import org.example.datn.domain.enums.PaymentMethod;
import org.example.datn.domain.enums.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TransactionService transactionService;

    /** Creates the PENDING payment record alongside a new order. */
    @Transactional
    public Payment createForOrder(Order order) {
        return paymentRepository.save(Payment.builder()
                .order(order)
                .method(order.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount())
                .build());
    }

    /** COD is collected on hand-off, so completion settles the payment. */
    @Transactional
    public void markCodPaidOnCompletion(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.COD) {
            return;
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        paymentRepository.findByOrderOrderId(order.getOrderId()).ifPresent(p -> {
            p.setStatus(PaymentStatus.PAID);
            p.setPaidAt(LocalDateTime.now());
            paymentRepository.save(p);
        });
    }

    @Transactional
    public void refundIfPaid(Order order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.findByOrderOrderId(order.getOrderId()).ifPresent(p -> {
            p.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(p);
        });
        transactionService.recordRefund(order);
        log.info("Refund initiated for order {}", order.getOrderId());
    }

    /** Called by the VNPay IPN handler. */
    @Transactional
    public void applyVnpayResult(Long orderId, boolean success, String transactionNo) {
        Order order = orderRepository.findByIdOrThrow(orderId, ErrorCode.ORDER_NOT_FOUND);
        Payment payment = paymentRepository.findByOrderOrderId(orderId)
                .orElseGet(() -> createForOrder(order));

        if (success) {
            order.setPaymentStatus(PaymentStatus.PAID);
            payment.setStatus(PaymentStatus.PAID);
            payment.setTransactionNo(transactionNo);
            payment.setPaidAt(LocalDateTime.now());
        } else {
            order.setPaymentStatus(PaymentStatus.FAILED);
            payment.setStatus(PaymentStatus.FAILED);
        }
        paymentRepository.save(payment);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order requireOrder(Long orderId) {
        return orderRepository.findByIdOrThrow(orderId, ErrorCode.ORDER_NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public void assertCustomerOwnsOrder(Long orderId, Long customerId) {
        Order order = requireOrder(orderId);
        if (!order.getCustomer().getUserId().equals(customerId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}
