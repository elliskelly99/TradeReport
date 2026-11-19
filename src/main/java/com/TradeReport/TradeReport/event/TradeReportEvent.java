package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * @param eventId    unique per change; the consumer's idempotency key
 * @param version    TradeReport optimistic-lock version at the time of the change
 * @param user       who made the change (audit "changed_by")
 * @param occurredAt when the change happened (used as the audit time)
 */
public record TradeReportEvent(UUID eventId, Long reportId, Long version, BigDecimal amount, String firm,
                               Instant createdOn, TradeReportStatus eventType, String user,
                               Instant occurredAt) {

    public static TradeReportEvent of(TradeReport entity, TradeReportStatus eventType, String user) {
        return new TradeReportEvent(UUID.randomUUID(), entity.getId(), entity.getVersion(),
                entity.getAmount(), entity.getFirm(), entity.getCreatedOn(), eventType, user, Instant.now());
    }

    public TradeReportEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(firm, "firm must not be null");
        Objects.requireNonNull(createdOn, "createdOn must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
