package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;

public class updateResponse {
    private final Long reportId;
    private final BigDecimal amount;
    private final String firm;
    private final String user;
    private final Long version;

    public updateResponse(Long reportId, BigDecimal amount, String firm, String user, Long version) {
        this.reportId = reportId;
        this.amount = amount;
        this.firm = firm;
        this.user = user;
        this.version = version;
    }

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
