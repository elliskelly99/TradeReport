package com.TradeReport.TradeReport.entities;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class TradeReportsAudits {

    public TradeReportsAudits(TradeReport tradeReport, TradeReportStatus tradeReportStatus,
                                   String user){
        this.tradeReport = tradeReport;
        this.auditTime = Instant.now();
        this.tradeReportStatus = tradeReportStatus;
        this.user = user;
    }
    protected TradeReportsAudits(){}

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Long Id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_report_id")
    TradeReport tradeReport;

    @Column(name = "changed_By")
    String user;

    Instant auditTime;

    TradeReportStatus tradeReportStatus;

    public Long getId() {
        return Id;
    }

    public TradeReport getTradeReport() {
        return tradeReport;
    }

    public String getUser() {
        return user;
    }
    public Instant getDate() {
        return auditTime;
    }

}
