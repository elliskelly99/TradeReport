package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TradeReportServiceUnitTest {

    @InjectMocks
    TradeReportService tradeReportService;
    @Mock
    TradeReportRepository tradeReportRepository;

    @Test
    public void getReportReturnTradeReport(){
        TradeReport tradeReport = new TradeReport(new BigDecimal("5.00"), "test", "test");

    when(tradeReportRepository.findById(1L)).thenReturn(Optional.of(tradeReport));

        TradeReport tradeReportCall = tradeReportService.getReport(1L);

        assert tradeReportCall.getFirm().equals(tradeReport.getFirm());

    }

    @Test
    public void createNewReportFailDueToDuplicateTradeRefAndFirm(){
        List<CreateNewReport> reports = List.of(
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "test1", "ELLIS"),
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "test1", "ELLIS"));

        Exception e = assertThrows(RuntimeException.class, ()-> tradeReportService.createNewReportBulk(reports));

        assert(e.getMessage().contains("Duplicate report found"));
    }


}
