package com.TradeReport.TradeReport.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;


public record CreateNewReport(@NotNull @Positive BigDecimal amount, @NotBlank String firm, @NotBlank String tradeRefId, @NotBlank String user) {

}
