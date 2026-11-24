package com.TradeReport.TradeReport.controller;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.DTO.CreateNewReportResponse;
import com.TradeReport.TradeReport.DTO.UpdateAmountAndFirm;
import com.TradeReport.TradeReport.DTO.UpdateResponse;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.service.TradeReportService;
import jakarta.validation.Valid;
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

    @GetMapping("/getReportRequestMapping")
    public ResponseEntity<TradeReport> getReportRequestMapping(@RequestParam Long id){
        TradeReport tradeReport = tradeReportService.getReport(id);
        return ResponseEntity.ok(tradeReport);
    }

    @PostMapping("/CreateNewReport")
    public CreateNewReportResponse createNewReport(@Validated @RequestBody CreateNewReport req){
        return tradeReportService.createNewReport(req);

    }

    @PutMapping("/updateReport")
    public ResponseEntity<UpdateResponse> updateReport(@Validated @RequestBody UpdateAmountAndFirm req) {
        UpdateResponse updatedTradeReport = tradeReportService.updateAmountAndFirm(req);
        return ResponseEntity.ok(updatedTradeReport);
    }

    @PutMapping("/updateReportBulk")
    public ResponseEntity<List<UpdateResponse>> updateReportsBulk(@RequestBody List<@Valid UpdateAmountAndFirm> reqs){

        List<UpdateResponse> responses = new ArrayList<>();

        for(UpdateAmountAndFirm report: reqs){
            UpdateResponse response = tradeReportService.updateAmountAndFirm(report);
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
