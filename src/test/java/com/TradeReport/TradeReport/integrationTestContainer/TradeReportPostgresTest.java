package com.TradeReport.TradeReport.integrationTestContainer;

import com.TradeReport.TradeReport.DTO.ReportPage;
import com.TradeReport.TradeReport.DTO.ReportSortColumn;
import com.TradeReport.TradeReport.DTO.ReportSummary;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.event.TradeReportEvent;
import com.TradeReport.TradeReport.service.TradeReportAuditService;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a real Postgres (Testcontainers) with the real Flyway migrations.
 * These check things H2 cannot: constraints and triggers that exist only in the migrations.
 * Tests are not @Transactional on purpose: each call commits, like production. Each test uses
 * its own firm name so tests do not collide.
 */
@SpringBootTest
@Import(PostgresTestConfig.class)
@ActiveProfiles("pg")
class TradeReportPostgresTest {

    @Autowired TradeReportRepository reportRepository;
    @Autowired TradeReportService service;
    @Autowired TradeReportAuditService auditService;
    @Autowired JdbcTemplate jdbc;

    private static String firm() {
        return "firm-" + UUID.randomUUID();
    }

    @Test
    void sameFirmAndTradeRefIsRejectedByUniqueConstraint() {
        String firm = firm();
        reportRepository.saveAndFlush(new TradeReport(new BigDecimal("10.00"), firm, "REF-1"));

        Exception e = assertThrows(DataIntegrityViolationException.class,
                () -> reportRepository.saveAndFlush(new TradeReport(new BigDecimal("20.00"), firm, "REF-1")));
        assert(e.getMessage().contains("uq_trade_report_firm_ref"));
    }

    @Test
    void searchFiltersSortsAndPagesInTheDatabase() {
        String tag = "srch" + UUID.randomUUID().toString().substring(0, 8);
        reportRepository.saveAndFlush(new TradeReport(new BigDecimal("30.00"), tag + "-Alpha", "R1"));
        reportRepository.saveAndFlush(new TradeReport(new BigDecimal("10.00"), tag + "-Beta", "R2"));
        reportRepository.saveAndFlush(new TradeReport(new BigDecimal("20.00"), tag + "-Gamma", "R3"));

        // Matches anywhere in the text, ignoring case; sorted by amount, 2 per page.
        ReportPage page = service.searchReports(0, 2, ReportSortColumn.amount, Sort.Direction.ASC,
                tag.toUpperCase(), "", null);
        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(List.of("10.00", "20.00"),
                page.content().stream().map(r -> r.getAmount().toPlainString()).toList());

        // The lastChange sort (updated time, else created time) is valid SQL and gives oldest first.
        ReportPage byTime = service.searchReports(0, 3, ReportSortColumn.lastChange, Sort.Direction.ASC, tag, "", null);
        assertEquals(List.of("R1", "R2", "R3"), byTime.content().stream().map(TradeReport::getTradeRefId).toList());

        // A literal % in the search text is not a wildcard, so nothing matches.
        assertEquals(0, service.searchReports(0, 50, ReportSortColumn.id, Sort.Direction.DESC, "%", "", null).totalElements());

        // Status filter and the summary over all matching rows.
        assertEquals(0, service.searchReports(0, 50, ReportSortColumn.id, Sort.Direction.DESC, tag, "", TradeReportStatus.CANCEL).totalElements());
        ReportSummary summary = service.summarize(tag, "", null);
        assertEquals(3L, summary.statusCounts().get(TradeReportStatus.OPEN));
        assertEquals(0L, summary.statusCounts().get(TradeReportStatus.CANCEL));
        assertEquals(3, summary.firmTotals().size());
        assertEquals(tag + "-Alpha", summary.firmTotals().get(0).firm()); // biggest total first
    }

    @Test
    void sameTradeRefIsAllowedForDifferentFirms() {
        reportRepository.saveAndFlush(new TradeReport(new BigDecimal("10.00"), firm(), "REF-SHARED"));
        reportRepository.saveAndFlush(new TradeReport(new BigDecimal("10.00"), firm(), "REF-SHARED"));
    }

    @Test
    void duplicateEventIdCannotCreateASecondAuditRow() {
        TradeReport report = reportRepository.saveAndFlush(new TradeReport(new BigDecimal("5.00"), firm(), "REF-2"));
        TradeReportEvent event = TradeReportEvent.of(report, TradeReportStatus.OPEN, "ellis");

        auditService.recordAudit(event);
        DataIntegrityViolationException e = assertThrows( DataIntegrityViolationException.class, ()-> auditService.recordAudit(event));


        Integer rows = jdbc.queryForObject(
                "select count(*) from trade_reports_audits where event_id = ?", Integer.class, event.eventId());
        assert(e.getMessage().contains("uq_audit_event_id"));
        assertEquals(1, rows);
    }

    @Test
    void auditRowsCannotBeUpdatedOrDeleted() {
        TradeReport report = reportRepository.saveAndFlush(new TradeReport(new BigDecimal("5.00"), firm(), "REF-3"));
        TradeReportEvent event = TradeReportEvent.of(report, TradeReportStatus.OPEN, "ellis");
        auditService.recordAudit(event);

        Exception update = assertThrows(Exception.class, () -> jdbc.update(
                "update trade_reports_audits set changed_by = 'hacker' where event_id = ?", event.eventId()));
        assertTrue(update.getMessage().contains("append-only"), update.getMessage());

        Exception delete = assertThrows(Exception.class, () -> jdbc.update(
                "delete from trade_reports_audits where event_id = ?", event.eventId()));
        assertTrue(delete.getMessage().contains("append-only"), delete.getMessage());
    }

    @Test
    void auditCanNotExistWithoutExistingTradeReport(){
        TradeReportEvent tradeReportEvent = new TradeReportEvent(UUID.randomUUID(),111L, "trade-ref-1",1L,new BigDecimal("444.00"),
                firm(), Instant.now(), TradeReportStatus.OPEN,"ELLIS",Instant.now());
        Exception e = assertThrows(DataIntegrityViolationException.class, () -> auditService.recordAudit(tradeReportEvent));
        assert(e.getMessage().contains("violates foreign key constraint"));
    }



}
