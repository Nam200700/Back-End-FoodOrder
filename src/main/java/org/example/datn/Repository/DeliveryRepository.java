package org.example.datn.Repository;

import org.example.datn.domain.Delivery;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepository extends BaseRepository<Delivery, Long> {

    Optional<Delivery> findByOrderOrderId(Long orderId);
}
