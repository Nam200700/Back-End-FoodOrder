package org.example.datn.Repository;

import org.example.datn.domain.Delivery;
import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepository extends BaseRepository<Delivery, Long> {

    Optional<Delivery> findByOrderOrderId(Long orderId);

    @Query("SELECT d FROM Delivery d WHERE d.shipper.userId = :shipperId " +
            "AND (:status IS NULL OR d.order.orderStatus = :status) " +
            "ORDER BY d.createdAt DESC")
    Page<Delivery> findByShipperIdAndOrderStatus(
            @Param("shipperId") Long shipperId,
            @Param("status") OrderStatus orderStatus,
            Pageable pageable);
}