package com.TradeReport.TradeReport.exception;

/**
 * A report with this (firm, tradeRefId) already exists, so the create is refused (HTTP 409).
 * Creation is strict (ADR 0003): the client must update the existing report on purpose, a repeat
 * create never changes anything. If two creates race past the early check, the unique constraint
 * stops the loser and the client gets the same 409 (see DataIntegrityViolationException handling).
 */
public class ReportAlreadyExistsException extends RuntimeException {
    public ReportAlreadyExistsException(String firm, String tradeRefId) {
        super("A report already exists for firm='" + firm + "', tradeRefId='" + tradeRefId + "'");
    }
}
