package com.TradeReport.TradeReport.controller;

import com.TradeReport.TradeReport.DTO.createNewReport;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeReportController {
    @Autowired
    TradeReportService tradeReportService;

    @PostMapping("/createNewReport")
    public Long createNewReport(@Validated @RequestBody createNewReport req){
        return tradeReportService.createNewReport(req);

    }

}
