package com.TradeReport.TradeReport.service;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entity.TradeReportRepository;
import com.TradeReport.TradeReport.exception.DuplicateTradeRefException;
import com.TradeReport.TradeReport.exception.ReportNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

        Exception e = assertThrows(DuplicateTradeRefException.class, ()-> tradeReportService.createNewReportBulk(reports));

        assert(e.getMessage().contains("Duplicate trade reference"));
    }

    @Test
    public void uniqueKeyHasFirmFirstAndTradeRefSecond(){
        CreateNewReport.UniqueKey key =
                new CreateNewReport(BigDecimal.ONE, "FirmA", "REF-1", "ELLIS").uniqueKey();

        assertEquals("FirmA", key.firm());
        assertEquals("REF-1", key.tradeRefId());
    }

    @Test
    public void recentReportsAreCappedAndSortedNewestFirst(){
        when(tradeReportRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        tradeReportService.getRecentReports(10_000); // far too big: capped
        tradeReportService.getRecentReports(0);      // too small: raised to 1

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(tradeReportRepository, times(2)).findAll(pages.capture());

        assertEquals(200, pages.getAllValues().get(0).getPageSize());
        assertEquals(1, pages.getAllValues().get(1).getPageSize());
        assertEquals(Sort.Direction.DESC, pages.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    public void auditHistoryForAMissingReportThrowsNotFound(){
        when(tradeReportRepository.existsById(99L)).thenReturn(false);

        assertThrows(ReportNotFoundException.class,
                () -> tradeReportService.getReportAuditHistoryByReportId(99L));
    }


}
