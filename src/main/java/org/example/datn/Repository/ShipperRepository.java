package org.example.datn.Repository;

import org.example.datn.domain.Shipper;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperRepository extends BaseRepository<Shipper, Long> {

    Optional<Shipper> findByUserUserId(Long userId);

    /** Nạp Shipper của nhiều user 1 lượt — khử N+1 khi map danh sách hồ sơ shipper đã duyệt. */
    List<Shipper> findByUserUserIdIn(Collection<Long> userIds);
    boolean existsByLicensePlate(String licensePlate);

    // Đếm số shipper đang online (phục vụ card "Tài xế đang hoạt động" ở dashboard admin)
    long countByIsOnlineTrue();
}
