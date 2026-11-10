package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.createNewReport;
import com.TradeReport.TradeReport.DTO.updateAmountAndFirm;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.repository.TradeReportRepository;
import com.TradeReport.TradeReport.repository.TradeReportsAuditsRepository;
import com.zaxxer.hikari.util.IsolationLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Service
public class TradeReportService {
    @Autowired
    TradeReportRepository tradeReportRepository;

    @Autowired
    TradeReportsAuditsRepository tradeReportsAuditsRepository;

    public Long createNewReport(createNewReport report){
        TradeReport mappedReport = new TradeReport(report.getAmount(), report.getFirm());
        TradeReport savedReport = tradeReportRepository.save(mappedReport);

        TradeReportsAudits tradeReportsAudits = new TradeReportsAudits(savedReport, TradeReportStatus.OPEN, report.getUser());
        tradeReportsAuditsRepository.save(tradeReportsAudits);

        return savedReport.getId();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cancelReport(Long Id, String user){
        TradeReport tradeReport = tradeReportRepository.findById(Id).orElseThrow(
                ()-> new RuntimeException("Cannot find report")
        );

        if(tradeReport.getTradeReportStatus().equals(TradeReportStatus.OPEN)){
            TradeReportsAudits tradeReportsAudits = new TradeReportsAudits(tradeReport, TradeReportStatus.CANCEL, user);
        }
    }

    public TradeReport getReport(Long Id){
        return tradeReportRepository.findById(Id).orElseThrow( ()->
                new RuntimeException("Can not find report"));
    }

    public List<TradeReportsAudits> getReportAuditHistoryByReportId(Long Id){
        return tradeReportsAuditsRepository.findByTradeReportId(Id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateAmountAndFirm(updateAmountAndFirm updates){
            Long id = updates.getReportId();
            BigDecimal amount = updates.getAmount();
            String firm =  updates.getFirm();

            TradeReport tradeReport = tradeReportRepository.findById(id).orElseThrow( ()
             -> new RuntimeException(" Cannot find trade report"));

            TradeReportsAudits tradeReportsAudits = new TradeReportsAudits(tradeReport, TradeReportStatus.UPDATE, updates.getUser());

            tradeReportsAuditsRepository.save(tradeReportsAudits);

            tradeReport.setFirm(firm);
            tradeReport.setAmount(amount);
    }

}
