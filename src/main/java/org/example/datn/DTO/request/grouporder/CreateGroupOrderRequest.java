package org.example.datn.DTO.request.grouporder;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGroupOrderRequest {

    @NotNull(message = "Vui lòng chọn nhà hàng")
    private Long restaurantId;

    /** Địa chỉ từ sổ địa chỉ có sẵn. Nếu null thì dùng deliveryAddress/lat/lng nhập tay. */
    private Long addressId;

    private String deliveryAddress;

    private BigDecimal deliveryLat;

    private BigDecimal deliveryLng;

    /** Hạn chót thành viên chọn món xong. Null = không giới hạn. */
    private LocalDateTime joinDeadline;

    private String note;
}
