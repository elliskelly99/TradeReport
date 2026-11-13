package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;

/**
 * @param amount @Positive(message = "Amount must be greater than 0.00" )
 * @param user   @NotNull(message = "User must be valid")
 */
public record createNewReport(BigDecimal amount, String firm, String user) {

}
