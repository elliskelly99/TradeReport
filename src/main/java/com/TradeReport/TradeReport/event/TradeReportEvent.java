package com.TradeReport.TradeReport.event;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TradeReportEvent(Long reportId, BigDecimal amount, String firm, Instant createdOn,// "CREATED" or "UPDATED"
                               TradeReportStatus eventType)
{

    // Map directly from TradeReport entity
    public TradeReportEvent(TradeReport entity) {
        this(
                entity.getId(),
                entity.getAmount(),
                entity.getFirm(),
                entity.getCreatedOn(),
                entity.getTradeReportStatus()
        );
    }
    // Compact constructor: no parentheses or parameter list
    public TradeReportEvent {
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(firm, "firm must not be null");
        Objects.requireNonNull(createdOn, "createdOn must not be null");
        Objects.requireNonNull(eventType, "tradeReportStatus must not be null");
    }


}
