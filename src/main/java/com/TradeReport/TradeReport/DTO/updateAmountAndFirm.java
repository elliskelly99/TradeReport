package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;

public record updateAmountAndFirm(Long reportId, BigDecimal amount, String firm, String user) {
}
