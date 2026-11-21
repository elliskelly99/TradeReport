package com.TradeReport.TradeReport.DTO;

import com.TradeReport.TradeReport.entities.TradeReportStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateNewReportResponse(BigDecimal amount, String firm, String tradeRefID,Instant createdOn,
                                      TradeReportStatus tradeReportStatus) {
}
