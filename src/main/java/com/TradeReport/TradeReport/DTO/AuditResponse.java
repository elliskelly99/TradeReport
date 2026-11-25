package com.TradeReport.TradeReport.DTO;

import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;

import java.time.Instant;
import java.util.UUID;

/** One row of a report's audit history, as the API returns it (the entity itself is not exposed). */
public record AuditResponse(Long id, UUID eventId, String tradeRefId, Long reportVersion,
                            TradeReportStatus tradeReportStatus, String user, Instant auditTime) {

    public static AuditResponse from(TradeReportsAudits audit) {
        return new AuditResponse(audit.getId(), audit.getEventId(), audit.getTradeRefId(),
                audit.getReportVersion(), audit.getTradeReportStatus(), audit.getUser(), audit.getDate());
    }
}
