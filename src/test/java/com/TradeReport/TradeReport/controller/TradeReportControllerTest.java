package com.TradeReport.TradeReport.controller;

import com.TradeReport.TradeReport.DTO.CreateNewReport;
import com.TradeReport.TradeReport.service.TradeReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeReportController.class)
public class TradeReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean//INJECT INTO THE SPRING CONTEXT DUMMY SERVICE UNLESS MOCKED
    private TradeReportService tradeReportService;

    @Test
    public void rejectsInvalidRequestBodyAtRequestLevel() throws Exception {
        List<CreateNewReport> invalid = List.of(new CreateNewReport(null, "", "", ""));

        mockMvc.perform(post("/createNewReportBulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void acceptsValidRequestBodyAtRequestLevelNoServiceEntry() throws Exception {
        List<CreateNewReport> valid = List.of(
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "test1", "ELLIS"),
                new CreateNewReport(BigDecimal.ONE, "EAK Investment", "test2", "ELLIS"));

        mockMvc.perform(post("/createNewReportBulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(valid)))
                .andExpect(status().isOk());
    }
}