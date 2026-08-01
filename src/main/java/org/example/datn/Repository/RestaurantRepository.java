package org.example.datn.Repository;

import org.example.datn.domain.Restaurant;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends BaseRepository<Restaurant, Long> {

    Page<Restaurant> findByStatusTrue(Pageable pageable);

    List<Restaurant> findByOwnerUserId(Long ownerId);

    // Tăng trưởng quán đối tác cho dashboard admin (theo created_at)
    long countByCreatedAtAfter(java.time.LocalDateTime since);

    // Chống trùng số điện thoại quán (quán đã được duyệt)
    boolean existsByPhone(String phone);
}
