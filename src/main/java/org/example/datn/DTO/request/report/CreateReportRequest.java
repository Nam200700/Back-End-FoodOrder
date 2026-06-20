package org.example.datn.DTO.request.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.datn.domain.enums.ReportTargetType;

@Data
public class CreateReportRequest {

    @NotNull(message = "Loại đối tượng không được để trống")
    private ReportTargetType targetType;

    @NotNull(message = "targetId không được để trống")
    private Long targetId;

    @NotBlank(message = "Lý do không được để trống")
    private String reason;
}
