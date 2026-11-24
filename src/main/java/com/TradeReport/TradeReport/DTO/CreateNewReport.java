package com.TradeReport.TradeReport.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.Locale;


public record CreateNewReport(@NotNull @Positive BigDecimal amount, @NotBlank String firm, @NotBlank String tradeRefId, @NotBlank String user) {
    public record UniqueKey(String firm, String tradeRefId){}

    public UniqueKey uniqueKey(){
        return new UniqueKey(tradeRefId.trim().toLowerCase(), firm.trim().toLowerCase());
    }

}
