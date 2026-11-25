package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One change to a trade report, published to Kafka and turned into one audit row by the consumer.
 *
 * @param eventId    unique per change; the consumer's idempotency key (unique audit.event_id)
 * @param reportId   the report this change belongs to; also the Kafka message key, so all events
 *                   for one report go to the same partition
 * @param tradeRefId the report's upstream trade reference. It identifies the report together with
 *                   the firm and never changes after creation (see ADR 0003)
 * @param version    TradeReport optimistic-lock version at the time of the change; use it to order
 *                   a report's history, because events can arrive out of order
 * @param amount     report amount after the change
 * @param firm       firm on the report after the change
 * @param createdOn  when the report was first created
 * @param eventType  what kind of change this is (OPEN, UPDATE, ...)
 * @param user       who made the change (audit "changed_by")
 * @param occurredAt when the change happened (used as the audit time)
 */
public record TradeReportEvent(UUID eventId, Long reportId, String tradeRefId,Long version, BigDecimal amount, String firm,
                               Instant createdOn, TradeReportStatus eventType, String user,
                               Instant occurredAt) {

    public static TradeReportEvent of(TradeReport entity, TradeReportStatus eventType, String user){

        return new TradeReportEvent(UUID.randomUUID(), entity.getId(),entity.getTradeRefId(), entity.getVersion(),
                entity.getAmount(), entity.getFirm(), entity.getCreatedOn(), eventType, user, Instant.now());

    }

    public TradeReportEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(tradeRefId, "tradeRefId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(firm, "firm must not be null");
        Objects.requireNonNull(createdOn, "createdOn must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
