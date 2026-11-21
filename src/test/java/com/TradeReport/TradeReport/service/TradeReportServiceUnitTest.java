package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.repository.TradeReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
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



}
