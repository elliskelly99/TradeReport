package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.DTO.CreateNewReportResponse;
import com.TradeReport.TradeReport.DTO.UpdateAmountAndFirm;
import com.TradeReport.TradeReport.DTO.UpdateResponse;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.event.TradeReportEvent;
import com.TradeReport.TradeReport.entities.OutboxEvent;
import com.TradeReport.TradeReport.entity.OutboxEventRepository;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.entity.TradeReportsAuditsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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
        The change and its outbox row commit together;
        the relay publishes the event and the Kafka consumer writes the audit (idempotent feature).
     */
    @Transactional
    public CreateNewReportResponse createNewReport(CreateNewReport report){
        TradeReport mappedReport = new TradeReport(report.amount(), report.firm(), report.tradeRefId());
        TradeReport savedReport = tradeReportRepository.saveAndFlush(mappedReport);

        outboxEventRepository.save(new OutboxEvent(
                TradeReportEvent.of(savedReport, TradeReportStatus.OPEN, report.user())));

        return new CreateNewReportResponse(savedReport.getAmount(), savedReport.getFirm(),savedReport.getTradeRefId(), savedReport.getCreatedOn(),
                savedReport.getTradeReportStatus());
    }

    //DESIGN DECISION: ATOMIC BULK UPLOAD SUCCEED OR FAIL TOGETHER
    @Transactional
    public List<CreateNewReportResponse> createNewReportBulk(List<CreateNewReport> reports){
       //FAIL FAST DESIGN: CHECK THAT USER HAS UNIQUE TRADE REF ID AND FIRM FROM REQUEST
        HashSet<CreateNewReport.UniqueKey> seenKeys = new HashSet<>();

        for(CreateNewReport report: reports){
            CreateNewReport.UniqueKey key = new CreateNewReport.UniqueKey(report.firm(), report.tradeRefId());

            if(!seenKeys.contains(key)){
                seenKeys.add(key);
            }
            else{
                throw new RuntimeException("Duplicate report found, ensure trade reference unique for each firm");
            }
        }

        List<TradeReport> reportList = new ArrayList<>();

        for(CreateNewReport report: reports) {
            reportList.add(new TradeReport(report.amount(), report.firm(), report.tradeRefId()));
        }

        List<TradeReport> savedTradeReports = tradeReportRepository.saveAllAndFlush(reportList);

        List<OutboxEvent> outboxEvents = new ArrayList<>(savedTradeReports.size());
        for (int i = 0; i < savedTradeReports.size(); i++) {
            outboxEvents.add(new OutboxEvent(
                    TradeReportEvent.of(savedTradeReports.get(i), TradeReportStatus.OPEN, reports.get(i).user())
            ));
        }
        outboxEventRepository.saveAllAndFlush(outboxEvents);

        List<CreateNewReportResponse> savedReportResponses = new ArrayList<>();
        for(TradeReport savedReport: savedTradeReports){
            savedReportResponses.add(new CreateNewReportResponse(savedReport.getAmount(), savedReport.getFirm(), savedReport.getTradeRefId(),savedReport.getCreatedOn(),
                savedReport.getTradeReportStatus() ));
        }

        return savedReportResponses;
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
        return tradeReportsAuditsRepository.findByTradeReportIdOrderByReportVersionAsc(Id);
    }

    /*
     Design choice:
         optimistic locking on report entity handles concurrency
         Trade off: No retry
  */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpdateResponse updateAmountAndFirm(UpdateAmountAndFirm updates){
            Long id = updates.reportId();
            BigDecimal amount = updates.amount();
            String firm =  updates.firm();

            TradeReport tradeReport = tradeReportRepository.findById(id).orElseThrow( ()
             -> new RuntimeException(" Cannot find trade report"));

            tradeReport.setFirm(firm);
            tradeReport.setAmount(amount);
            tradeReport.setUpdatedOn(Instant.now());
            tradeReport.setStatus(TradeReportStatus.UPDATE);

            TradeReport updatedReport = tradeReportRepository.saveAndFlush(tradeReport);

            outboxEventRepository.save(new OutboxEvent(
                    TradeReportEvent.of(updatedReport, TradeReportStatus.UPDATE, updates.user())));

        return new UpdateResponse(updatedReport.getId(), updatedReport.getAmount(),
                    updatedReport.getFirm(), updates.user(), updatedReport.getVersion());

    }

}
