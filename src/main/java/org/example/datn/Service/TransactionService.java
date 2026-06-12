package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.Repository.TransactionRepository;
import org.example.datn.domain.Order;
import org.example.datn.domain.Transaction;
import org.example.datn.domain.enums.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    /**
     * On completion: merchant earns the food subtotal, shipper earns the
     * shipping fee. Called inside the order-completion transaction.
     */
    @Transactional
    public void recordOrderTransactions(Order order) {
        transactionRepository.save(Transaction.builder()
                .order(order)
                .user(order.getRestaurant().getOwner())
                .type(TransactionType.MERCHANT_EARNING)
                .amount(order.getSubtotalAmount())
                .build());

        if (order.getShipper() != null) {
            transactionRepository.save(Transaction.builder()
                    .order(order)
                    .user(order.getShipper())
                    .type(TransactionType.SHIPPER_EARNING)
                    .amount(order.getShippingFee())
                    .build());
        }
    }

    @Transactional
    public void recordRefund(Order order) {
        transactionRepository.save(Transaction.builder()
                .order(order)
                .user(order.getCustomer())
                .type(TransactionType.REFUND)
                .amount(order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount())
                .build());
    }
}
