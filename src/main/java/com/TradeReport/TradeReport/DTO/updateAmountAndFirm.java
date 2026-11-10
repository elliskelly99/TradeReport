package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;

public class updateAmountAndFirm {
    public updateAmountAndFirm(Long reportId, BigDecimal amount, String firm, String user){
        this.reportId = reportId;
        this.amount = amount;
        this.firm = firm;
        this.user = user;
    }
    private final Long reportId;
    private final BigDecimal amount;
    private final String firm;
    private final String user;

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
