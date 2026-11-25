package com.TradeReport.TradeReport.entities;

import com.TradeReport.TradeReport.event.TradeReportEvent;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. Written in the same DB transaction as the report change,
 * then relayed to Kafka by {@code OutboxRelay}. Guarantees a committed change is never
 * lost between "DB commit" and "Kafka send".
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "trade_ref_id", nullable = false)
    private String tradeRefId;

    @Column(name = "report_version", nullable = false)
    private Long reportVersion;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String firm;

    @Column(name = "report_created_on", nullable = false)
    private Instant reportCreatedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TradeReportStatus eventType;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(TradeReportEvent e) {
        this.eventId = e.eventId();
        this.reportId = e.reportId();
        this.tradeRefId = e.tradeRefId();
        this.reportVersion = e.version();
        this.amount = e.amount();
        this.firm = e.firm();
        this.reportCreatedOn = e.createdOn();
        this.eventType = e.eventType();
        this.changedBy = e.user();
        this.occurredAt = e.occurredAt();
    }

    public TradeReportEvent toEvent() {
        return new TradeReportEvent(eventId, reportId, tradeRefId, reportVersion, amount, firm,
                reportCreatedOn, eventType, changedBy, occurredAt);
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
