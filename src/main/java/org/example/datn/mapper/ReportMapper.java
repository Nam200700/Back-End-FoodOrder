package org.example.datn.mapper;

import org.example.datn.domain.Report;
import org.example.datn.DTO.response.report.ReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReportMapper {

    @Mapping(source = "reporter.userId", target = "reporterId")
    ReportResponse toResponse(Report report);
}
