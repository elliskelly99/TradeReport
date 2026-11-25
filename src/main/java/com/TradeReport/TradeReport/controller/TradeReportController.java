package com.TradeReport.TradeReport.controller;

import com.TradeReport.TradeReport.DTO.AuditResponse;
import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.DTO.CreateNewReportResponse;
import com.TradeReport.TradeReport.DTO.ReportPage;
import com.TradeReport.TradeReport.DTO.ReportSortColumn;
import com.TradeReport.TradeReport.DTO.ReportSummary;
import com.TradeReport.TradeReport.DTO.UpdateAmount;
import com.TradeReport.TradeReport.DTO.UpdateResponse;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.service.TradeReportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class TradeReportController {

    private final TradeReportService tradeReportService;

    TradeReportController(TradeReportService tradeReportService){
        this.tradeReportService = tradeReportService;
    }

    @GetMapping("/getReport/{id}")
    public ResponseEntity<TradeReport> getReport(@PathVariable Long id){
        TradeReport tradeReport = tradeReportService.getReport(id);
        return ResponseEntity.ok(tradeReport);
    }

    /**
     * One page of reports. Pages start at 0; size defaults to 50 and is capped at 200.
     * sort is one of ReportSortColumn and dir is ASC or DESC (default: newest first).
     * firm and tradeRefId match anywhere in the text, ignoring case; status is optional.
     */
    @GetMapping("/getReports")
    public ResponseEntity<ReportPage> getReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "id") ReportSortColumn sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction dir,
            @RequestParam(defaultValue = "") String firm,
            @RequestParam(defaultValue = "") String tradeRefId,
            @RequestParam(required = false) TradeReportStatus status){
        return ResponseEntity.ok(tradeReportService.searchReports(page, size, sort, dir, firm, tradeRefId, status));
    }

    /** Count per status and the biggest firms, over all matching reports. Same filters as getReports. */
    @GetMapping("/getReportSummary")
    public ResponseEntity<ReportSummary> getReportSummary(
            @RequestParam(defaultValue = "") String firm,
            @RequestParam(defaultValue = "") String tradeRefId,
            @RequestParam(required = false) TradeReportStatus status){
        return ResponseEntity.ok(tradeReportService.summarize(firm, tradeRefId, status));
    }

    /** Audit history of one report, oldest version first. Audit rows are written by the Kafka consumer, so a new change may take about a second to appear. */
    @GetMapping("/getReportAudits/{id}")
    public ResponseEntity<List<AuditResponse>> getReportAudits(@PathVariable Long id){
        List<AuditResponse> audits = tradeReportService.getReportAuditHistoryByReportId(id)
                .stream()
                .map(AuditResponse::from)
                .toList();
        return ResponseEntity.ok(audits);
    }

    @GetMapping("/getReportRequestMapping")
    public ResponseEntity<TradeReport> getReportRequestMapping(@RequestParam Long id){
        TradeReport tradeReport = tradeReportService.getReport(id);
        return ResponseEntity.ok(tradeReport);
    }

    @PostMapping("/createNewReport")
    public CreateNewReportResponse createNewReport(@Validated @RequestBody CreateNewReport req){
        return tradeReportService.createNewReport(req);

    }

    @PutMapping("/updateReport")
    public ResponseEntity<UpdateResponse> updateReport(@Validated @RequestBody UpdateAmount req) {
        UpdateResponse updatedTradeReport = tradeReportService.updateAmount(req);
        return ResponseEntity.ok(updatedTradeReport);
    }

    @PutMapping("/updateReportBulk")
    public ResponseEntity<List<UpdateResponse>> updateReportsBulk(@RequestBody List<@Valid UpdateAmount> reqs){

        List<UpdateResponse> responses = new ArrayList<>();

        for(UpdateAmount report: reqs){
            UpdateResponse response = tradeReportService.updateAmount(report);
            if (response != null) {
                responses.add(response);
            } else {
                responses.add(null);
            }
        }
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/createNewReportBulk")
    public ResponseEntity<List<CreateNewReportResponse>>  createNewReportBulk(@RequestBody List<@Valid CreateNewReport> reqs) {

        List<CreateNewReportResponse> savedReports = tradeReportService.createNewReportBulk(reqs);

        return ResponseEntity.ok(savedReports);
    }
}
