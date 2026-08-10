package org.example.datn.DTO.request.voucher;

import jakarta.validation.constraints.*;
import lombok.*;
import org.example.datn.domain.enums.DiscountType;
import org.example.datn.domain.enums.VoucherIssueType;
import org.example.datn.domain.enums.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherRequest {

    @NotBlank(message = "Mã voucher không được để trống")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Tên voucher không được để trống")
    @Size(max = 255)
    private String name;

    @NotNull(message = "Loại giảm giá không được để trống")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.0", message = "Giá trị đơn tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.0", message = "Mức giảm tối đa không được âm")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDateTime endDate;

    @NotNull(message = "Trạng thái không được để trống")
    private VoucherStatus status;

    @NotNull(message = "Loại phát voucher không được để trống")
    private VoucherIssueType issueType;
}