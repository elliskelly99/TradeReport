package com.TradeReport.TradeReport.exception;

/** No trade report exists with the requested id. Mapped to HTTP 404. */
public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(Long id) {
        super("Trade report " + id + " not found");
    }
}
