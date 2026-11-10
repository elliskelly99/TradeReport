package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;

public class updateAmountAndFirm {
    private Long reportId;
    private BigDecimal amount;
    private String firm;
    private String user;

    public Long getReportId() {
        return reportId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFirm() {
        return firm;
    }

    public String getUser(){
        return user;
    }
}
