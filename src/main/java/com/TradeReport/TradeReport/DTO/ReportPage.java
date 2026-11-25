package com.TradeReport.TradeReport.DTO;

import com.TradeReport.TradeReport.entities.TradeReport;

import java.util.List;

/** One page of reports plus the numbers a UI needs to show "page 3 of 120". Pages start at 0. */
public record ReportPage(List<TradeReport> content, int page, int size, long totalElements, int totalPages) {
}
