package com.TradeReport.TradeReport.entities;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class TradeReport {
    public TradeReport(BigDecimal amount, String firm){
        this.amount = amount;
        this.firm = firm;
        this.createdOn = Instant.now();
        this.tradeReportStatus = TradeReportStatus.OPEN;
    }

    protected TradeReport() {}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String firm;

    private Instant createdOn;

    private Instant updatedOn;

    private TradeReportStatus tradeReportStatus;

    public Long getId() {
        return id;
    }

    @Version
    private Long version;

    public Long getVersion() {
        return version;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getFirm() {
        return firm;
    }

    @Enumerated(EnumType.STRING)
    public TradeReportStatus getTradeReportStatus(){return tradeReportStatus;}

    public void setFirm(String firm) {
        this.firm = firm;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Instant createdOn) {
        this.createdOn = createdOn;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setStatus(TradeReportStatus tradeReportStatus){this.tradeReportStatus = tradeReportStatus;}
}
