package org.example.datn.Service;

import lombok.RequiredArgsConstructor;
import org.example.datn.common.PageResponse;
import org.example.datn.domain.Report;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.domain.enums.ReportTargetType;
import org.example.datn.DTO.request.report.CreateReportRequest;
import org.example.datn.DTO.response.report.ReportResponse;
import org.example.datn.Exception.ErrorCode;
import org.example.datn.mapper.ReportMapper;
import org.example.datn.Repository.ReportRepository;
import org.example.datn.Repository.RestaurantRepository;
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
    private final RestaurantRepository restaurantRepository;
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
        return toResponseWithTarget(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(ReportStatus status, Pageable pageable) {
        var page = (status == null)
                ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page.map(this::toResponseWithTarget));
    }

    @Transactional
    public ReportResponse resolve(Long reportId, ReportStatus status) {
        Report report = reportRepository.findByIdOrThrow(reportId, ErrorCode.NOT_FOUND);
        report.setStatus(status);
        report.setResolvedAt(LocalDateTime.now());
        return toResponseWithTarget(reportRepository.save(report));
    }

    /** Map sang DTO rồi bổ sung tên đối tượng bị báo cáo (mapper không tự tra được vì targetId là id thô). */
    private ReportResponse toResponseWithTarget(Report report) {
        ReportResponse res = reportMapper.toResponse(report);
        res.setTargetName(resolveTargetName(report.getTargetType(), report.getTargetId()));
        return res;
    }

    /** Tra tên hiển thị của đối tượng bị báo cáo theo loại; không tìm thấy thì trả nhãn kèm #id. */
    private String resolveTargetName(ReportTargetType type, Long id) {
        if (type == null || id == null) return "Không xác định";
        switch (type) {
            case RESTAURANT:
                return restaurantRepository.findById(id)
                        .map(r -> r.getRestaurantName())
                        .orElse("Quán #" + id);
            case SHIPPER:
                return userRepository.findById(id)
                        .map(u -> u.getFullName())
                        .orElse("Tài xế #" + id);
            case USER:
                return userRepository.findById(id)
                        .map(u -> u.getFullName())
                        .orElse("Người dùng #" + id);
            case ORDER:
                return "Đơn hàng #" + id;
            case REVIEW:
                return "Đánh giá #" + id;
            default:
                return "#" + id;
        }
    }
}
