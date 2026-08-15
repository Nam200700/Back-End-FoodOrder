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

    Optional<Otp> findFirstByRecipientAndPurposeOrderByCreatedAtDesc(String recipient, OtpPurpose purpose);

    Optional<Otp> findFirstByRecipientAndPurposeAndIsUsedFalseOrderByCreatedAtDesc(String recipient, OtpPurpose purpose);

    @Modifying
    @Query("UPDATE Otp o SET o.isUsed = true WHERE o.recipient = :recipient AND o.purpose = :purpose AND o.isUsed = false")
    void invalidateOldOtps(@Param("recipient") String recipient, @Param("purpose") OtpPurpose purpose);

    /** Đếm số mã đã cấp cho một người nhận kể từ mốc thời gian — dùng để chặn spam theo ngày. */
    long countByRecipientAndCreatedAtAfter(String recipient, java.time.LocalDateTime since);
}
