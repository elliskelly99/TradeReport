package com.TradeReport.TradeReport.exception;

/**
 * The same (firm, tradeRefId) appears more than once inside one bulk request.
 * That is a bad request, so it maps to HTTP 400. A clash with a row already in the
 * database is different: the unique constraint raises DataIntegrityViolationException (HTTP 409).
 */
public class DuplicateTradeRefException extends RuntimeException {
    public DuplicateTradeRefException(String firm, String tradeRefId) {
        super("Duplicate trade reference in request: firm='" + firm + "', tradeRefId='" + tradeRefId + "'");
    }
}
