package com.nexora.ticket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.nexora.config.*;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.common.pagination.PageResponse;
import com.nexora.ticket.dto.request.AssignTicketRequest;
import com.nexora.ticket.dto.response.EligibleTechnicianResponse;
import com.nexora.ticket.dto.response.TicketActionResultResponse;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.service.TicketAssignmentService;
import com.nexora.ticket.service.TicketReadService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtRoleConverter.class, ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class TicketControllerMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean TicketReadService service;
    @MockitoBean TicketAssignmentService assignmentService;
    @MockitoBean JwtDecoder decoder;

    @Test
    void missingAuthenticationAndInvalidTokenAre401() throws Exception {
        mvc.perform(get("/api/v1/tickets")).andExpect(status().isUnauthorized());
        when(decoder.decode("invalid")).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("internal detail"));
        mvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."));
    }
    @Test
    void listReturnsSharedPageEnvelope() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/tickets").with(jwt().jwt(j -> j.subject("10"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalPages").value(0));
    }
    @ParameterizedTest
    @ValueSource(strings = {"?sort=title,asc", "?priority=CRITICAL", "?page=-1", "?page=2147483647", "?size=101", "?locationId=0",
            "?status=", "?unknown=test", "?page=0&page=1", "?sort=createdAt,desc&sort=updatedAt,asc"})
    void invalidListParametersAre400AndDoNotQuery(String query) throws Exception {
        mvc.perform(get("/api/v1/tickets" + query).with(jwt())).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(service);
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number", "9223372036854775808"})
    void invalidPathIsSafe400(String id) throws Exception {
        mvc.perform(get("/api/v1/tickets/" + id).with(jwt())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexora.app/problems/validation-failed"));
        verifyNoInteractions(service);
    }

    @Test
    void eligibleTechniciansReturnsSharedPageEnvelope() throws Exception {
        when(assignmentService.eligibleTechnicians(any(), eq(11L), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/tickets/11/eligible-technicians").with(jwt().jwt(j -> j.subject("10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?query=", "?eligibility=UNKNOWN",
            "?eligibility=", "?page=0&page=1", "?unknown=val"})
    void invalidEligibleTechniciansParametersAre400(String query) throws Exception {
        mvc.perform(get("/api/v1/tickets/11/eligible-technicians" + query).with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(assignmentService);
    }

    @Test
    void assignValidatesPayloadAndInvokesService() throws Exception {
        when(assignmentService.assign(any(), eq(11L), any()))
                .thenReturn(new TicketActionResultResponse(11L, TicketStatus.ASSIGNED, 1L, null));
        mvc.perform(post("/api/v1/tickets/11/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"technicianId\":25}")
                        .with(jwt().jwt(j -> j.subject("10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"version\":-1,\"technicianId\":25}", "{\"version\":0,\"technicianId\":-1}",
            "{\"version\":0}", "{\"technicianId\":25}"})
    void invalidAssignPayloadReturns400(String payload) throws Exception {
        mvc.perform(post("/api/v1/tickets/11/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(jwt().jwt(j -> j.subject("10"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(assignmentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number", "9223372036854775808"})
    void invalidActionPathReturnsFieldErrors(String id) throws Exception {
        for (String action : List.of("review", "assign")) {
            String body = action.equals("review") ? "{\"version\":0}" : "{\"version\":0,\"technicianId\":25}";
            mvc.perform(post("/api/v1/tickets/" + id + "/" + action).with(jwt())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.ticketId").exists());
        }
        verifyNoInteractions(assignmentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "[]", "{\"version\":0} {\"version\":1}"})
    void malformedActionJsonDoesNotInvokeService(String body) throws Exception {
        for (String action : List.of("review", "assign")) {
            mvc.perform(post("/api/v1/tickets/10/" + action).with(jwt())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.fieldErrors.body").exists());
        }
        verifyNoInteractions(assignmentService);
    }
}
