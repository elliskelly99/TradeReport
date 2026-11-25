package com.TradeReport.TradeReport.DTO;

/**
 * The columns the report list can be sorted by. Only these are allowed, so a client can never
 * ask the database to sort by an arbitrary field. Status is left out on purpose.
 */
public enum ReportSortColumn {
    id, tradeRefId, firm, amount, version, lastChange
}
