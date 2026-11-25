package com.TradeReport.TradeReport.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Update request. Only the amount can change. The report's identity, firm and tradeRefId,
 * is fixed at creation (ADR 0003), so it is not part of this request.
 */
public record UpdateAmount(@NotNull Long reportId, @Positive @NotNull BigDecimal amount, @NotBlank String user) {
}
