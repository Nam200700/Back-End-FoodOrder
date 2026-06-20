package org.example.datn.Repository;

import org.example.datn.domain.Report;
import org.example.datn.domain.enums.ReportStatus;
import org.example.datn.Repository.base.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends BaseRepository<Report, Long> {

    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
