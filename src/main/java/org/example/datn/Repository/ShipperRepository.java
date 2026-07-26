package org.example.datn.Repository;

import org.example.datn.domain.Shipper;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShipperRepository extends BaseRepository<Shipper, Long> {

    Optional<Shipper> findByUserUserId(Long userId);
    boolean existsByLicensePlate(String licensePlate);

    // Đếm số shipper đang online (phục vụ card "Tài xế đang hoạt động" ở dashboard admin)
    long countByIsOnlineTrue();
}
