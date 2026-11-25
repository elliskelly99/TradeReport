package com.TradeReport.TradeReport.DTO;

import java.math.BigDecimal;


public class UpdateResponse {
    private final Long reportId;
    private final String tradeRefId;
    private final String firm;
    private final BigDecimal amount;
    private final String user;
    private final Long version;

    public UpdateResponse(Long reportId, String tradeRefId, String firm, BigDecimal amount, String user, Long version) {
        this.reportId = reportId;
        this.tradeRefId = tradeRefId;
        this.firm = firm;
        this.amount = amount;
        this.user = user;
        this.version = version;
    }

    public Long getReportId() {
        return reportId;
    }

    public String getTradeRefId() {
        return tradeRefId;
    }

    public String getFirm() {
        return firm;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getUser(){
        return user;
    }

    public Long getVersion() {
        return version;
    }
}
