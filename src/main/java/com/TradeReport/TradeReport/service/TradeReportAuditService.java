package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.event.TradeReportEvent;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.entity.TradeReportsAuditsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeReportAuditService {

    private final TradeReportsAuditsRepository auditsRepository;
    private final TradeReportRepository reportRepository;

    public TradeReportAuditService(TradeReportsAuditsRepository auditsRepository,
                                   TradeReportRepository reportRepository) {
        this.auditsRepository = auditsRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * Inserts one audit row for the event. Throws DataIntegrityViolationException if the
     * event_id was already stored (duplicate delivery); the caller decides what that means.
     */
    @Transactional
    public void recordAudit(TradeReportEvent event) {
        TradeReportsAudits audit = new TradeReportsAudits(
                event.eventId(),
                reportRepository.getReferenceById(event.reportId()),
                event.version(),
                event.eventType(),
                event.user(),
                event.occurredAt());
        auditsRepository.saveAndFlush(audit);
    }
}
