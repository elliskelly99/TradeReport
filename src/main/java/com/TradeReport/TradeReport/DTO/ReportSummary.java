package com.TradeReport.TradeReport.DTO;

import com.TradeReport.TradeReport.entities.TradeReportStatus;

import java.util.List;
import java.util.Map;

/** Numbers over all matching reports (not just one page): count per status and the biggest firms. */
public record ReportSummary(Map<TradeReportStatus, Long> statusCounts, List<FirmTotal> firmTotals) {
}
