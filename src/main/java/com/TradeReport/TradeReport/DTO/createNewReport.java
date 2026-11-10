package com.TradeReport.TradeReport.DTO;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.aspectj.bridge.Message;

import java.math.BigDecimal;

public class createNewReport {

    public createNewReport(BigDecimal amount, String firm, String user) {
        this.amount = amount;
        this.firm = firm;
        this.user = user;
    }
    @Positive(message = "Amount must be greater than 0.00" )
    private final BigDecimal amount;
    private final String firm;
    @NotNull(message = "User must be valid")
    private final String user;

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFirm() {
        return firm;
    }

    public String getUser() {
        return user;
    }
}
