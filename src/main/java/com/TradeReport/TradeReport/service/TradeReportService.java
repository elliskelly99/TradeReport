package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.DTO.CreateNewReportResponse;
import com.TradeReport.TradeReport.DTO.UpdateAmount;
import com.TradeReport.TradeReport.DTO.UpdateResponse;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.event.TradeReportEvent;
import com.TradeReport.TradeReport.exception.DuplicateTradeRefException;
import com.TradeReport.TradeReport.exception.ReportNotFoundException;
import com.TradeReport.TradeReport.entities.OutboxEvent;
import com.TradeReport.TradeReport.entity.OutboxEventRepository;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.entity.TradeReportsAuditsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    static final int MAX_LIST_SIZE = 200;

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

        return new CreateNewReportResponse(savedReport.getId(),savedReport.getAmount(), savedReport.getFirm(),savedReport.getTradeRefId(), savedReport.getCreatedOn(),
                savedReport.getTradeReportStatus());
    }

    //DESIGN DECISION: ATOMIC BULK UPLOAD SUCCEED OR FAIL TOGETHER
    @Transactional
    public List<CreateNewReportResponse> createNewReportBulk(List<CreateNewReport> reports){
       //FAIL FAST DESIGN: CHECK THAT USER REQUEST HAS UNIQUE TRADE REF ID AND FIRM FROM REQUEST
        HashSet<CreateNewReport.UniqueKey> seenKeys = new HashSet<>();
    
        for(CreateNewReport report: reports){
            if (!seenKeys.add(report.uniqueKey())) {
                throw new DuplicateTradeRefException(report.firm(), report.tradeRefId());
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
            savedReportResponses.add(new CreateNewReportResponse(savedReport.getId(),savedReport.getAmount(), savedReport.getFirm(), savedReport.getTradeRefId(),savedReport.getCreatedOn(),
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
                () -> new ReportNotFoundException(Id)
        );

        if(tradeReport.getTradeReportStatus().equals(TradeReportStatus.OPEN)){
            TradeReportsAudits tradeReportsAudits = new TradeReportsAudits(tradeReport, TradeReportStatus.CANCEL, user);
        }

        // TODO NOTE: cancelReport is still incomplete (pre-existing, out of scope): it saves nothing and is not exposed.
    }

    public TradeReport getReport(Long Id){
        return tradeReportRepository.findById(Id).orElseThrow(
                () -> new ReportNotFoundException(Id));
    }

    /** Audit rows for one report, oldest version first. 404 if the report does not exist. */
    public List<TradeReportsAudits> getReportAuditHistoryByReportId(Long Id){
        if (!tradeReportRepository.existsById(Id)) {
            throw new ReportNotFoundException(Id);
        }
        return tradeReportsAuditsRepository.findByTradeReportIdOrderByReportVersionAsc(Id);
    }

    /** The newest reports first. The limit is kept between 1 and MAX_LIST_SIZE so one call cannot return the whole table. */
    public List<TradeReport> getRecentReports(int limit){
        int size = Math.max(1, Math.min(limit, MAX_LIST_SIZE));
        return tradeReportRepository.findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "id"))).getContent();
    }

    /*
     Design choice:
         optimistic locking on report entity handles concurrency
         Trade off: No retry
  */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpdateResponse updateAmount(UpdateAmount updates){
            Long id = updates.reportId();
            BigDecimal amount = updates.amount();

            TradeReport tradeReport = tradeReportRepository.findById(id).orElseThrow(
                    () -> new ReportNotFoundException(id));

            tradeReport.setAmount(amount);
            tradeReport.setUpdatedOn(Instant.now());
            tradeReport.setStatus(TradeReportStatus.UPDATE);

            TradeReport updatedReport = tradeReportRepository.saveAndFlush(tradeReport);

            outboxEventRepository.save(new OutboxEvent(
                    TradeReportEvent.of(updatedReport, TradeReportStatus.UPDATE, updates.user())));

        return new UpdateResponse(updatedReport.getId(), updatedReport.getTradeRefId(), updatedReport.getFirm(),
                    updatedReport.getAmount(), updates.user(), updatedReport.getVersion());

    }

}
