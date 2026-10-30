package com.nexora.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.pagination.PageResponse;
import com.nexora.config.ProblemAccessDeniedHandler;
import com.nexora.config.ProblemAuthenticationEntryPoint;
import com.nexora.config.SecurityConfig;
import com.nexora.report.dto.TicketReportResponse;
import com.nexora.report.dto.TicketReportSummaryResponse;
import com.nexora.report.service.TicketReportService;
import com.nexora.report.service.TicketReportService.ExportResult;
import com.nexora.ticket.entity.TicketStatus;

@WebMvcTest(controllers = TicketReportController.class)
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class
})
class TicketReportControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketReportService reportService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("report: Unauthenticated request returns 401")
    void report_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("export: Unauthenticated request returns 401")
    void export_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets/export")
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("report: Requester or Technician without management role returns 403")
    void report_nonManager_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_REQUESTER")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));

        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("export: Requester returns 403")
    void export_nonManager_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets/export")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_REQUESTER")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("report: Manager role with valid query returns 200 and report payload")
    void report_manager_validQuery_returns200() throws Exception {
        Map<TicketStatus, Long> counts = new EnumMap<>(TicketStatus.class);
        for (TicketStatus s : TicketStatus.values()) counts.put(s, 0L);
        counts.put(TicketStatus.IN_PROGRESS, 2L);

        TicketReportSummaryResponse summary = new TicketReportSummaryResponse(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 7),
                "Asia/Bangkok",
                OffsetDateTime.parse("2026-10-07T12:00:00+07:00"),
                2L,
                1L,
                counts
        );
        PageResponse<com.nexora.ticket.dto.response.TicketSummaryResponse> page =
                new PageResponse<>(List.of(), 0, 20, 2L, 1);
        when(reportService.getReport(any())).thenReturn(new TicketReportResponse(summary, page));

        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.summary.totalTickets").value(2))
                .andExpect(jsonPath("$.summary.overdueTickets").value(1))
                .andExpect(jsonPath("$.summary.statusCounts.IN_PROGRESS").value(2))
                .andExpect(jsonPath("$.tickets.totalElements").value(2));
    }

    @Test
    @DisplayName("export: Admin role returns 200 and CSV with correct headers")
    void export_admin_validQuery_returns200() throws Exception {
        byte[] csv = "\uFEFFMã phiếu,Tiêu đề\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(reportService.exportReport(any())).thenReturn(new ExportResult("report.csv", csv));

        mockMvc.perform(get("/api/v1/reports/tickets/export")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\""))
                .andExpect(content().bytes(csv));
    }

    @Test
    @DisplayName("report: Missing required date parameters returns 400")
    void report_missingDateParams_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("report: fromDate after toDate returns 400")
    void report_fromDateAfterToDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-10-10")
                        .param("toDate", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("report: Range exceeding 92 days returns 400")
    void report_rangeExceeding92Days_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("report: Unknown parameter returns 400")
    void report_unknownParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07")
                        .param("foo", "bar"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("export: Pagination parameter (page) rejected with 400")
    void export_withPaginationParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/tickets/export")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
    }

    @Test
    @DisplayName("export: Exceeding 10000 tickets returns 422")
    void export_exceedsMaxLimit_returns422() throws Exception {
        when(reportService.exportReport(any())).thenThrow(new BusinessRuleException("The export query matches 10005 tickets, exceeding the maximum limit of 10,000 tickets."));

        mockMvc.perform(get("/api/v1/reports/tickets/export")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER")))
                        .param("fromDate", "2026-10-01")
                        .param("toDate", "2026-10-07"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/business-rule-violation"))
                .andExpect(jsonPath("$.detail").value("The export query matches 10005 tickets, exceeding the maximum limit of 10,000 tickets."));
    }
}

