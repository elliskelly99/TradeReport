package com.TradeReport.TradeReport.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record updateAmountAndFirm(@NotNull Long reportId, @Positive@NotNull BigDecimal amount,@NotNull String firm, @NotBlank String user) {
}
