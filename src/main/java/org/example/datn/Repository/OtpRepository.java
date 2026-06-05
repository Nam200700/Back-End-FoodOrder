package org.example.datn.Repository;

import org.example.datn.domain.Otp;
import org.example.datn.domain.enums.OtpPurpose;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends BaseRepository<Otp, Long> {

    Optional<Otp> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    Optional<Otp> findFirstByPhoneAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    @Modifying
    @Query("UPDATE Otp o SET o.isUsed = true WHERE o.phone = :phone AND o.purpose = :purpose AND o.isUsed = false")
    void invalidateOldOtps(@Param("phone") String phone, @Param("purpose") OtpPurpose purpose);
}
