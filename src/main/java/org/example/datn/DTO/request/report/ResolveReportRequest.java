package org.example.datn.DTO.request.report;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.example.datn.domain.enums.ReportStatus;

@Data
public class ResolveReportRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private ReportStatus status;
}
