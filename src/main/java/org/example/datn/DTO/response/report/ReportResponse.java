package org.example.datn.DTO.response.report;

import lombok.Builder;
import lombok.Data;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.domain.enums.ReportTargetType;

import java.time.LocalDateTime;

@Data
@Builder
public class ReportResponse {
    private Long reportId;
    private Long reporterId;
    private ReportTargetType targetType;
    private Long targetId;
    private String reason;
    private ReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
