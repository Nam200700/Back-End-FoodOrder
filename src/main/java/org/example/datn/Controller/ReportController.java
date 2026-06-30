package org.example.datn.Controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.datn.common.ApiResponse;
import org.example.datn.DTO.request.report.CreateReportRequest;
import org.example.datn.DTO.response.report.ReportResponse;
import org.example.datn.security.CustomUserDetails;
import org.example.datn.Service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CreateReportRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(reportService.create(user.getUserId(), req)));
    }
}
