package org.example.datn.Repository;

import org.example.datn.Repository.base.BaseRepository;
import org.example.datn.domain.CustomerAddress;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerAddressRepository extends BaseRepository<CustomerAddress, Long> {
    List<CustomerAddress> findByCustomerUserId(Long customerId);

    @Modifying
    @Query("UPDATE CustomerAddress c SET c.isDefault = false WHERE c.customer.userId = :customerId")
    void resetDefaultAddressByCustomerId(@Param("customerId") Long customerId);
}
