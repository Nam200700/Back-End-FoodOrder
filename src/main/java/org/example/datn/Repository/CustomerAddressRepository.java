package org.example.datn.Repository;

import org.example.datn.domain.CustomerAddress;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerAddressRepository extends BaseRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerUserId(Long customerId);

    Optional<CustomerAddress> findByCustomerUserIdAndIsDefaultTrue(Long customerId);
}