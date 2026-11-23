package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entity.OutboxEventRepository;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.entity.TradeReportsAuditsRepository;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TradeReportConsumerTest {

    @Autowired TradeReportConsumer consumer;
    @Autowired TradeReportRepository reportRepository;
    @Autowired TradeReportsAuditsRepository auditsRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired TradeReportService service;

    private TradeReport newReport() {
        return reportRepository.saveAndFlush(new TradeReport(new BigDecimal("10.00"), "firmA", "10L"));
    }

    @Test
    void duplicateDeliveryCreatesOneAuditRow() {
        TradeReport report = newReport();
        TradeReportEvent event = TradeReportEvent.of(report, TradeReportStatus.OPEN, "ellis");

        consumer.consume(event, 0, 1L);
        consumer.consume(event, 0, 1L); // redelivery

        assertEquals(1, auditsRepository.findByTradeReportIdOrderByReportVersionAsc(report.getId()).size());
    }

    @Test
    void distinctEventsEachGetAnAuditRow() {
        TradeReport report = newReport();
        consumer.consume(TradeReportEvent.of(report, TradeReportStatus.OPEN, "a"), 0, 1L);
        consumer.consume(TradeReportEvent.of(report, TradeReportStatus.UPDATE, "b"), 0, 2L);

        assertEquals(2, auditsRepository.findByTradeReportIdOrderByReportVersionAsc(report.getId()).size());
    }

    @Test
    void missingReportIsNotSwallowedAsDuplicate() {
        TradeReportEvent event = new TradeReportEvent(UUID.randomUUID(), 999_999L, 0L,
                new BigDecimal("1.00"), "f", Instant.now(), TradeReportStatus.OPEN, "u", Instant.now());
        assertThrows(RuntimeException.class, () -> consumer.consume(event, 0, 1L));
    }

    @Test
    void createReportWritesOutboxRowButNoSynchronousAudit() {
        long outboxBefore = outboxRepository.count();
        long auditsBefore = auditsRepository.count();

        service.createNewReport(new CreateNewReport(new BigDecimal("5.00"), "firmB", "tradeRef","ellis"));

        assertEquals(outboxBefore + 1, outboxRepository.count());
        assertEquals(auditsBefore, auditsRepository.count());
    }
}
