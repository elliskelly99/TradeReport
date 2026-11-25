package com.TradeReport.TradeReport.integrationTestContainer;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.DTO.CreateNewReportResponse;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(PostgresTestConfig.class)
@ActiveProfiles("pg")
public class TradeReportServiceTest {
    @Autowired
    TradeReportService tradeReportService;

    @Test
    public void createNewReportFailDueToDuplicateTradeRefAndFirm(){
        List<CreateNewReport> reports = List.of(
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "trade-ref-1", "ELLIS"),
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "trade-ref-2", "ELLIS"));


        List<CreateNewReportResponse> responses = tradeReportService.createNewReportBulk(reports);


        assertEquals(2, responses.size());
        assert(responses.get(0).toString().contains("OPEN"));
        assert(responses.get(1).toString().contains("OPEN"));

    }
}
