package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Delivery;
import org.example.datn.domain.Order;
import org.example.datn.Repository.DeliveryRepository;
import org.example.datn.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createDelivery(Order order, Long shipperId) {
        Delivery delivery = Delivery.builder()
                .order(order)
                .shipper(userRepository.getReferenceById(shipperId))
                .assignedAt(LocalDateTime.now())
                .build();
        deliveryRepository.save(delivery);
    }

    @Transactional
    public void completeDelivery(Order order) {
        deliveryRepository.findByOrderOrderId(order.getOrderId()).ifPresent(d -> {
            d.setCompletedAt(LocalDateTime.now());
            deliveryRepository.save(d);
        });
    }
}
