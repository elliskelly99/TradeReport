package com.TradeReport.TradeReport.entities;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class TradeReport {
    public TradeReport(BigDecimal amount, String firm, String tradeRefId){
        this.amount = amount;
        this.firm = firm;
        this.tradeRefId = tradeRefId;
        this.createdOn = Instant.now();
        this.tradeReportStatus = TradeReportStatus.OPEN;
    }

    protected TradeReport() {}


    /*
        Idempotent feature:
            Unique trade ref ID
            and
            Firm
            to ensure retry tradeReport creations and events

        Upstream trade reference. It is unique per firm,
        so the same ref can appear once for each firm.
        The constraint uq_trade_report_firm_ref on (firm, trade_ref_id) is defined in the Flyway migration (V2), not here.
        Hibernate runs with ddl-auto=validate and does not create it, so change the constraint in the migration.


     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String tradeRefId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String firm;

    private Instant createdOn;

    private Instant updatedOn;

    @Enumerated(EnumType.STRING)
    private TradeReportStatus tradeReportStatus;

    public Long getId() {
        return id;
    }

    @Version
    private Long version;

    public String getTradeRefId(){return tradeRefId;}

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

    public TradeReportStatus getTradeReportStatus(){return tradeReportStatus;}

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
