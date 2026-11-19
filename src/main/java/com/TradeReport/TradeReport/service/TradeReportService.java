package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.createNewReport;
import com.TradeReport.TradeReport.DTO.createNewReportResponse;
import com.TradeReport.TradeReport.DTO.updateAmountAndFirm;
import com.TradeReport.TradeReport.DTO.updateResponse;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.event.TradeReportEvent;
import com.TradeReport.TradeReport.entities.OutboxEvent;
import com.TradeReport.TradeReport.repository.OutboxEventRepository;
import com.TradeReport.TradeReport.repository.TradeReportRepository;
import com.TradeReport.TradeReport.repository.TradeReportsAuditsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TradeReportService {
    @Autowired
    TradeReportRepository tradeReportRepository;

    @Autowired
    TradeReportsAuditsRepository tradeReportsAuditsRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    /*
        Audit rows are NOT written here any more. The change and its outbox row commit together;
        the relay publishes the event and the Kafka consumer writes the audit (idempotently).
     */
    @Transactional
    public createNewReportResponse createNewReport(createNewReport report){
        TradeReport mappedReport = new TradeReport(report.amount(), report.firm());
        TradeReport savedReport = tradeReportRepository.saveAndFlush(mappedReport);

        outboxEventRepository.save(new OutboxEvent(
                TradeReportEvent.of(savedReport, TradeReportStatus.OPEN, report.user())));

        return new createNewReportResponse(savedReport.getAmount(), savedReport.getFirm(), savedReport.getCreatedOn(), savedReport.getTradeReportStatus());
    }


    /*
        Design choice:
            optimistic locking on report entity handles concurrency
            Trade off: No retry
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelReport(Long Id, String user){
        TradeReport tradeReport = tradeReportRepository.findById(Id).orElseThrow(
                ()-> new RuntimeException("Cannot find report")
        );

        if(tradeReport.getTradeReportStatus().equals(TradeReportStatus.OPEN)){
            TradeReportsAudits tradeReportsAudits = new TradeReportsAudits(tradeReport, TradeReportStatus.CANCEL, user);
        }

        // NOTE: cancelReport is still incomplete (pre-existing, out of scope): it saves nothing and is not exposed.
    }

    public TradeReport getReport(Long Id){
        return tradeReportRepository.findById(Id).orElseThrow( ()->
                new RuntimeException("Can not find report"));
    }

    public List<TradeReportsAudits> getReportAuditHistoryByReportId(Long Id){
        return tradeReportsAuditsRepository.findByTradeReportId(Id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public updateResponse updateAmountAndFirm(updateAmountAndFirm updates){
            Long id = updates.reportId();
            BigDecimal amount = updates.amount();
            String firm =  updates.firm();

            TradeReport tradeReport = tradeReportRepository.findById(id).orElseThrow( ()
             -> new RuntimeException(" Cannot find trade report"));

            tradeReport.setFirm(firm);
            tradeReport.setAmount(amount);
            tradeReport.setStatus(TradeReportStatus.UPDATE);

            TradeReport updatedReport = tradeReportRepository.saveAndFlush(tradeReport);

            outboxEventRepository.save(new OutboxEvent(
                    TradeReportEvent.of(updatedReport, TradeReportStatus.UPDATE, updates.user())));

        return new updateResponse(updatedReport.getId(), updatedReport.getAmount(),
                    updatedReport.getFirm(), updates.user(), updatedReport.getVersion());

    }

}
