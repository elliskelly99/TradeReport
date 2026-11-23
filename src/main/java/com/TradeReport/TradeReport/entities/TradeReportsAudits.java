package com.TradeReport.TradeReport.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit row. {@code eventId} is unique so a redelivered Kafka message
 * can never create a second audit row (idempotent consumer).
 */
@Entity
@Immutable
public class TradeReportsAudits {

    public TradeReportsAudits(TradeReport tradeReport, TradeReportStatus tradeReportStatus,
                              String user) {
        this(UUID.randomUUID(), tradeReport, null, tradeReportStatus, user, Instant.now());
    }

    public TradeReportsAudits(UUID eventId, TradeReport tradeReport, Long reportVersion,
                              TradeReportStatus tradeReportStatus, String user, Instant auditTime) {
        this.eventId = eventId;
        this.tradeReport = tradeReport;
        this.reportVersion = reportVersion;
        this.tradeReportStatus = tradeReportStatus;
        this.user = user;
        this.auditTime = auditTime;
    }

    protected TradeReportsAudits() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long Id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_report_id")
    TradeReport tradeReport;

    @Column(name = "report_version")
    Long reportVersion;

    @Column(name = "changed_By")
    String user;

    Instant auditTime;

    @Enumerated(EnumType.STRING)
    TradeReportStatus tradeReportStatus;

    public Long getId() {
        return Id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public TradeReport getTradeReport() {
        return tradeReport;
    }

    public Long getReportVersion() {
        return reportVersion;
    }

    public String getUser() {
        return user;
    }

    public Instant getDate() {
        return auditTime;
    }

    public TradeReportStatus getTradeReportStatus() {
        return tradeReportStatus;
    }
}
