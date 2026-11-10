package com.TradeReport.TradeReport.DTO;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import org.aspectj.bridge.Message;

import java.math.BigDecimal;

public class createNewReport {

    public createNewReport(BigDecimal amount, String firm, String user) {
        this.amount = amount;
        this.firm = firm;
        this.user = user;
    }

    private final BigDecimal amount;
    private final String firm;
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
