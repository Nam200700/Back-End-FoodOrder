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
    private String reporterName;   // tên người gửi báo cáo (để admin xem trực tiếp thay vì #id)
    private ReportTargetType targetType;
    private Long targetId;
    private String targetName;     // tên đối tượng bị báo cáo (quán/tài xế/người dùng...), tra theo targetType
    private String reason;
    private ReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
