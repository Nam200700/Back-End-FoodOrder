package org.example.datn.Repository;

import org.example.datn.domain.Payment;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends BaseRepository<Payment, Long> {

    Optional<Payment> findByOrderOrderId(Long orderId);
}
