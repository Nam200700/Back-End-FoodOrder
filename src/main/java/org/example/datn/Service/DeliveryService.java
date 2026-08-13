package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.domain.Delivery;
import org.example.datn.domain.Order;
import org.example.datn.domain.enums.DeliveryStatus;
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
                .status(DeliveryStatus.ASSIGNED)
                .assignedAt(LocalDateTime.now())
                .build();
        deliveryRepository.save(delivery);
    }

    @Transactional
    public void completeDelivery(Order order) {
        // Lần gán đang hoạt động (ASSIGNED) mới nhất → đánh dấu hoàn tất.
        deliveryRepository.findFirstByOrderOrderIdAndStatusOrderByAssignedAtDesc(
                order.getOrderId(), DeliveryStatus.ASSIGNED).ifPresent(d -> {
            d.setStatus(DeliveryStatus.COMPLETED);
            d.setCompletedAt(LocalDateTime.now());
            deliveryRepository.save(d);
        });
    }

    /**
     * Shipper bỏ đơn: GIỮ bản ghi giao hàng (đánh dấu CANCELLED) để còn trong lịch sử của shipper,
     * đồng thời đơn vẫn về pool cho shipper khác nhận lại (đã bỏ ràng buộc unique order_id).
     */
    @Transactional
    public void cancelDelivery(Order order) {
        deliveryRepository.findFirstByOrderOrderIdAndStatusOrderByAssignedAtDesc(
                order.getOrderId(), DeliveryStatus.ASSIGNED).ifPresent(d -> {
            d.setStatus(DeliveryStatus.CANCELLED);
            deliveryRepository.save(d);
        });
    }
}
