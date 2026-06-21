package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Report;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.DTO.request.report.CreateReportRequest;
import org.example.datn.DTO.response.report.ReportResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.ReportMapper;
import org.example.datn.Repository.ReportRepository;
import org.example.datn.Repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportMapper reportMapper;

    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest req) {
        Report report = Report.builder()
                .reporter(userRepository.getReferenceById(reporterId))
                .targetType(req.getTargetType())
                .targetId(req.getTargetId())
                .reason(req.getReason())
                .status(ReportStatus.PENDING)
                .build();
        return reportMapper.toResponse(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(ReportStatus status, Pageable pageable) {
        var page = (status == null)
                ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page.map(reportMapper::toResponse));
    }

    @Transactional
    public ReportResponse resolve(Long reportId, ReportStatus status) {
        Report report = reportRepository.findByIdOrThrow(reportId, ErrorCode.NOT_FOUND);
        report.setStatus(status);
        report.setResolvedAt(LocalDateTime.now());
        return reportMapper.toResponse(reportRepository.save(report));
    }
}
