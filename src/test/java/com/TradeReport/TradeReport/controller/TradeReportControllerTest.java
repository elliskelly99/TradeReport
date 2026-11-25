package com.TradeReport.TradeReport.controller;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.entities.TradeReport;
import com.TradeReport.TradeReport.entities.TradeReportStatus;
import com.TradeReport.TradeReport.entities.TradeReportsAudits;
import com.TradeReport.TradeReport.exception.DuplicateTradeRefException;
import com.TradeReport.TradeReport.exception.ReportNotFoundException;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only: real controller and GlobalExceptionHandler, mocked service, no database, no Kafka.
 * Checks that each failure becomes the right HTTP status and a ProblemDetail body.
 */
@WebMvcTest(TradeReportController.class)
class TradeReportControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean TradeReportService service;

    private static final String VALID = """
            {"amount":10.50,"firm":"firmA","tradeRefId":"REF-1","user":"ellis"}""";


    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void rejectsInvalidRequestBodyAtRequestLevel() throws Exception {
        List<CreateNewReport> invalid = List.of(new CreateNewReport(null, "", "", ""));

        mvc.perform(post("/createNewReportBulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void acceptsValidRequestBodyAtRequestLevelNoServiceEntry() throws Exception {
        List<CreateNewReport> valid = List.of(
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "test1", "ELLIS"),
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "test2", "ELLIS"));

        mvc.perform(post("/createNewReportBulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isOk());
    }


    @Test
    void missingReportReturns404() throws Exception {
        when(service.getReport(99L)).thenThrow(new ReportNotFoundException(99L));

        mvc.perform(get("/getReport/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Report not found"))
                .andExpect(jsonPath("$.detail").value("Trade report 99 not found"));
    }

    @Test
    void invalidSingleBodyReturns400WithFieldErrors() throws Exception {
        String body = """
                {"amount":10.50,"firm":"firmA","tradeRefId":"REF-1","user":""}""";

        mvc.perform(post("/CreateNewReport").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*]", hasItem(containsString("user"))));
    }

    @Test
    void invalidItemInBulkBodyReturns400AndNamesTheItem() throws Exception {
        String body = "[" + VALID + ", {\"amount\":10.50,\"firm\":\"firmA\",\"tradeRefId\":\"REF-2\",\"user\":\"\"}]";

        mvc.perform(post("/createNewReportBulk").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*]", hasItem(containsString("[1].user"))));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/CreateNewReport").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateKeyInsideBulkRequestReturns400() throws Exception {
        when(service.createNewReportBulk(anyList()))
                .thenThrow(new DuplicateTradeRefException("firmA", "REF-1"));

        mvc.perform(post("/createNewReportBulk").contentType(MediaType.APPLICATION_JSON)
                        .content("[" + VALID + "," + VALID + "]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Duplicate trade reference"))
                .andExpect(jsonPath("$.detail", containsString("REF-1")));
    }

    @Test
    void uniqueConstraintClashReturns409WithoutLeakingSql() throws Exception {
        when(service.createNewReport(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; constraint [uq_trade_report_firm_ref]"));

        mvc.perform(post("/CreateNewReport").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail", not(containsString("uq_trade_report_firm_ref"))));
    }

    @Test
    void optimisticLockFailureReturns409() throws Exception {
        when(service.updateAmount(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        String body = """
                {"reportId":1,"amount":20.00,"user":"ellis"}""";

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/updateReport")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent update"));
    }

    @Test
    void unexpectedErrorReturns500WithGenericMessage() throws Exception {
        when(service.getReport(1L)).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(get("/getReport/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Something went wrong"))
                .andExpect(jsonPath("$.detail", not(containsString("secret"))));
    }

    // ---- List and audit endpoints ----

    @Test
    void getReportsReturnsTheList() throws Exception {
        when(service.getRecentReports(50))
                .thenReturn(List.of(new TradeReport(new BigDecimal("10.00"), "firmA", "REF-1")));

        mvc.perform(get("/getReports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firm").value("firmA"))
                .andExpect(jsonPath("$[0].tradeRefId").value("REF-1"));
    }

    @Test
    void getReportsPassesTheLimitToTheService() throws Exception {
        when(service.getRecentReports(5)).thenReturn(List.of());

        mvc.perform(get("/getReports").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).getRecentReports(5);
    }

    @Test
    void getReportAuditsReturnsTheHistory() throws Exception {
        TradeReport report = new TradeReport(new BigDecimal("10.00"), "firmA", "REF-1");
        when(service.getReportAuditHistoryByReportId(7L))
                .thenReturn(List.of(new TradeReportsAudits(report, TradeReportStatus.UPDATE, "ellis")));

        mvc.perform(get("/getReportAudits/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradeRefId").value("REF-1"))
                .andExpect(jsonPath("$[0].user").value("ellis"))
                .andExpect(jsonPath("$[0].tradeReportStatus").value("UPDATE"));
    }

    @Test
    void getReportAuditsForAMissingReportReturns404() throws Exception {
        when(service.getReportAuditHistoryByReportId(99L)).thenThrow(new ReportNotFoundException(99L));

        mvc.perform(get("/getReportAudits/99"))
                .andExpect(status().isNotFound());
    }

    // ---- CORS: which browser origins may call the API ----

    @Test
    void preflightFromTheDashboardOriginIsAllowed() throws Exception {
        mvc.perform(options("/CreateNewReport")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void preflightFromAnotherOriginIsRejected() throws Exception {
        mvc.perform(options("/CreateNewReport")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorResponsesStillCarryTheCorsHeader() throws Exception {
        when(service.getReport(99L)).thenThrow(new ReportNotFoundException(99L));

        mvc.perform(get("/getReport/99").header("Origin", "http://localhost:4200"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
