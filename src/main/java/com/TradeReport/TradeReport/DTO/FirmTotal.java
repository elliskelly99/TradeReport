package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;

/** Total amount of all reports of one firm. */
public record FirmTotal(String firm, BigDecimal total) {
}
