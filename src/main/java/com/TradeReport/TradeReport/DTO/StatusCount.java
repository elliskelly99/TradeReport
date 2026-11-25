package com.TradeReport.TradeReport.DTO;

import com.TradeReport.TradeReport.entities.TradeReportStatus;

/** How many reports have one status. */
public record StatusCount(TradeReportStatus status, Long count) {
}
