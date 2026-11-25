package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.DTO.CreateNewReportResponse;
import com.TradeReport.TradeReport.DTO.FirmTotal;
import com.TradeReport.TradeReport.DTO.ReportPage;
import com.TradeReport.TradeReport.DTO.ReportSortColumn;
import com.TradeReport.TradeReport.DTO.ReportSummary;
import com.TradeReport.TradeReport.DTO.StatusCount;
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
import com.TradeReport.TradeReport.exception.UpdateFailDuplicateAmountExistAlreadyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class TradeReportService {
    static final int MAX_LIST_SIZE = 200;
    static final int TOP_FIRMS = 6;

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

        if(tradeReportRepository.existsByTradeRefIdAndFirm(report.tradeRefId(), report.firm())){
            throw new DuplicateTradeRefException(report.firm(), report.tradeRefId());
        }

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

    /**
     * One page of reports. firm and tradeRef match anywhere in the text, ignoring case; status is optional.
     * The page size is kept between 1 and MAX_LIST_SIZE, so one call cannot return the whole table.
     * Ties are broken by id so a page never repeats or skips a row when many rows share a value.
     */
    public ReportPage searchReports(int page, int size, ReportSortColumn sortBy, Sort.Direction direction,
                                    String firm, String tradeRef, TradeReportStatus status) {
        int pageSize = Math.max(1, Math.min(size, MAX_LIST_SIZE));
        Sort sort = (sortBy == ReportSortColumn.lastChange
                ? JpaSort.unsafe(direction, "coalesce(r.updatedOn, r.createdOn)")
                : Sort.by(direction, sortBy.name()))
                .and(Sort.by(direction, "id"));

        Page<TradeReport> found = tradeReportRepository.search(
                likePattern(firm), likePattern(tradeRef), statusesFor(status),
                PageRequest.of(Math.max(0, page), pageSize, sort));

        return new ReportPage(found.getContent(), found.getNumber(), found.getSize(),
                found.getTotalElements(), found.getTotalPages());
    }

    /** Count per status (every status is present, zero when none) and the biggest firms, over all matching reports. */
    public ReportSummary summarize(String firm, String tradeRef, TradeReportStatus status) {
        String firmPattern = likePattern(firm);
        String tradeRefPattern = likePattern(tradeRef);

        Map<TradeReportStatus, Long> counts = new EnumMap<>(TradeReportStatus.class);
        for (TradeReportStatus s : TradeReportStatus.values()) {
            counts.put(s, 0L);
        }
        for (StatusCount row : tradeReportRepository.countByStatus(firmPattern, tradeRefPattern)) {
            counts.put(row.status(), row.count());
        }

        List<FirmTotal> firms = tradeReportRepository.topFirmTotals(
                firmPattern, tradeRefPattern, statusesFor(status), PageRequest.of(0, TOP_FIRMS));
        return new ReportSummary(counts, firms);
    }

    // "%text%" in lower case, with LIKE wildcards in the text made literal (escape character '!').
    private static String likePattern(String text) {
        String clean = text == null ? "" : text.trim().toLowerCase();
        return "%" + clean.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static List<TradeReportStatus> statusesFor(TradeReportStatus status) {
        return status == null ? List.of(TradeReportStatus.values()) : List.of(status);
    }

    /*
     Design choice:
         optimistic locking on report entity handles concurrency
         Trade off: No retry
  */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpdateResponse updateAmount(UpdateAmount updates){
        /*
            TODO: Check that update amount is different from the latest trade report version to ensure no duplicate
            Thinking about what happens when two request attempt to update the ticket
         */

            Long id = updates.reportId();
            BigDecimal amount = updates.amount();

            TradeReport tradeReport = tradeReportRepository.findById(id).orElseThrow(
                    () -> new ReportNotFoundException(id));

            // STALE CLIENT CHECK: the client edited an older version of this report, so reject (409)
            // instead of overwriting the newer change.
            if(!updates.version().equals(tradeReport.getVersion())){
                throw new ObjectOptimisticLockingFailureException(TradeReport.class, id);
            }

            //CHECK THAT THE AMOUNT IS DIFFERENT FROM THE CURRENT VERSION
            if(updates.amount().compareTo(tradeReport.getAmount()) == 0){
                throw new UpdateFailDuplicateAmountExistAlreadyException(tradeReport.getFirm(), tradeReport.getAmount());
            }

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
