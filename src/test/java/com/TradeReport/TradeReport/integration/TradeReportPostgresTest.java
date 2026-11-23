package com.TradeReport.TradeReport.integration;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.event.TradeReportEvent;
import com.TradeReport.TradeReport.service.TradeReportAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
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
        TradeReportEvent tradeReportEvent = new TradeReportEvent(UUID.randomUUID(),111L, 1L,new BigDecimal("444.00"),
                firm(), Instant.now(), TradeReportStatus.OPEN,"ELLIS",Instant.now());
        Exception e = assertThrows(DataIntegrityViolationException.class, () -> auditService.recordAudit(tradeReportEvent));
        assert(e.getMessage().contains("violates foreign key constraint"));
    }
}
