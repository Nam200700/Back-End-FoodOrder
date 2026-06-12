package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Order;
import org.example.datn.domain.Payment;
import org.example.datn.domain.Transaction;
import org.example.datn.domain.enums.PaymentStatus;
import org.example.datn.domain.enums.TransactionType;
import org.example.datn.Repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public void refundOrder(Order order, Payment payment) {
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        if (payment != null) payment.setStatus(PaymentStatus.REFUNDED);

        // Hoàn 100% cho khách
        Transaction refund = new Transaction();
        refund.setOrder(order);
        refund.setPayment(payment);
        refund.setUser(order.getCustomer());
        refund.setType(TransactionType.REFUND);
        refund.setAmount(order.getTotalAmount());
        transactionRepository.save(refund);

        // Đảo earning gốc
        List<Transaction> earnings = transactionRepository.findByOrderOrderIdAndTypeIn(
            order.getOrderId(),
            List.of(TransactionType.MERCHANT_EARNING, TransactionType.SHIPPER_EARNING));
        for (Transaction e : earnings) {
            if (e.getAmount().signum() <= 0) continue; // bỏ qua các bản đảo đã có
            Transaction rev = new Transaction();
            rev.setOrder(order);
            rev.setPayment(payment);
            rev.setUser(e.getUser());
            rev.setType(e.getType());
            rev.setAmount(e.getAmount().negate());
            transactionRepository.save(rev);
        }
    }
}
