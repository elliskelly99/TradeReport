package com.TradeReport.TradeReport.controller;

import com.TradeReport.TradeReport.DTO.createNewReport;
import com.TradeReport.TradeReport.DTO.updateAmountAndFirm;
import com.TradeReport.TradeReport.DTO.updateResponse;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class TradeReportController {
    @Autowired
    TradeReportService tradeReportService;

    @PostMapping("/createNewReport")
    public Long createNewReport(@Validated @RequestBody createNewReport req){
        return tradeReportService.createNewReport(req);

    }

    @PutMapping("/updateReport")
    public ResponseEntity<updateResponse> updateReport(@Validated @RequestBody updateAmountAndFirm req) {
        updateResponse updatedTradeReport = tradeReportService.updateAmountAndFirm(req);
        return ResponseEntity.ok(updatedTradeReport);


    }

    @PutMapping("/updateReportBulk")
    public ResponseEntity<List<updateResponse>> updateReportsBulk(@Validated @RequestBody List<updateAmountAndFirm> reqs){

        List<updateResponse> responses = new ArrayList<>();

        for(updateAmountAndFirm report: reqs){
            updateResponse response = tradeReportService.updateAmountAndFirm(report);
            if (response != null) {
                responses.add(response);
            } else {
                responses.add(null);
            }

        }
        return ResponseEntity.ok(responses);
    }
}
