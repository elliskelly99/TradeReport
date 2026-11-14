package com.TradeReport.TradeReport.DTO;

import com.TradeReport.TradeReport.entities.TradeReportStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record createNewReportResponse(BigDecimal amount, String firm, Instant createdOn,
                                      TradeReportStatus tradeReportStatus) {
}
