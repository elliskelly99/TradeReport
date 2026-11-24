package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.entity.TradeReportsAuditsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//Intergration test
//USES H2 DATABASE FOR SPEED
@SpringBootTest
class TradeReportServiceIntergrationTest {
    @Autowired
    TradeReportRepository tradeReportRepository;

    @Autowired
    TradeReportsAuditsRepository tradeReportsAuditsRepository;

    @Autowired
    TradeReportService tradeReportService;

    @Test
    void getReportAuditHistoryByReportId() {
        TradeReport tradeReport = new TradeReport(new BigDecimal("5.00"), "firmTest", "tradeRef");

        TradeReport savedReport = tradeReportRepository.save(tradeReport);

        TradeReportsAudits tradeReportsAudits = new TradeReportsAudits(savedReport, TradeReportStatus.UPDATE, "Ellis");
        TradeReportsAudits tradeReportsAudits2 = new TradeReportsAudits(savedReport, TradeReportStatus.UPDATE, "Ellis 2");
        TradeReportsAudits tradeReportsAudits3 = new TradeReportsAudits(savedReport, TradeReportStatus.UPDATE, "Ellis 3");

        tradeReportsAuditsRepository.save(tradeReportsAudits);
        tradeReportsAuditsRepository.save(tradeReportsAudits2);
        tradeReportsAuditsRepository.save(tradeReportsAudits3);

        List <TradeReportsAudits> result = tradeReportService.getReportAuditHistoryByReportId(1L);

        assertNotNull(result);
    }




}