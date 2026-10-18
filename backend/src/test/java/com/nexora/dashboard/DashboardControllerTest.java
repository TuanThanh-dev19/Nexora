package com.nexora.dashboard;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Set;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.auth.security.JwtRoleConverter;
import com.nexora.config.*;
import com.nexora.common.exception.GlobalExceptionHandler;
import com.nexora.dashboard.application.*;
import com.nexora.dashboard.controller.*;
import com.nexora.dashboard.dto.DashboardSummaryResponse;
import com.nexora.dashboard.port.DashboardSummaryQuery;
import com.nexora.user.entity.RoleCode;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, DashboardExceptionHandler.class,
        DefaultDashboardSummaryService.class, JwtRoleConverter.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class DashboardControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean DashboardSummaryQuery query;
    @MockitoBean JwtDecoder decoder;
    final String url = "/api/v1/dashboard/summary";
    void roles(String... roles) {
        when(auth.getCurrentUser(any())).thenReturn(new CurrentUserResponse(10L, "test@example.test", "Test", "ACTIVE", List.of(roles)));
    }
    @Test void missingTokenIs401() throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        verifyNoInteractions(query);
    }
    @Test void databaseRolesOverrideJwtClaimsAndKeepUnion() throws Exception {
        roles("REQUESTER", "TECHNICIAN");
        when(query.query(any())).thenReturn(new DashboardSummaryResponse(2,1,1,0,
                OffsetDateTime.parse("2026-10-15T10:30:00+07:00")));
        mvc.perform(get(url).with(jwt().authorities(() -> "ROLE_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.openTickets").value(2)).andExpect(jsonPath("$.generatedAt").value("2026-10-15T10:30:00+07:00"));
        verify(query).query(new DashboardViewer(10L, Set.of(RoleCode.REQUESTER, RoleCode.TECHNICIAN)));
    }
    @ParameterizedTest @ValueSource(strings = {"?role=ADMIN", "?userId=1", "?fromDate=2026-10-01", "?x=", "?role=ADMIN&role=REQUESTER"})
    void queryParametersAreRejected(String params) throws Exception {
        mvc.perform(get(url + params).with(jwt())).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(auth, query);
    }
    @Test void invalidIdentityAndNoRolesAreRejected() throws Exception {
        roles();
        mvc.perform(get(url).with(jwt())).andExpect(status().isForbidden());
        when(auth.getCurrentUser(any())).thenThrow(new InvalidCredentialsException());
        mvc.perform(get(url).with(jwt())).andExpect(status().isUnauthorized());
        verifyNoInteractions(query);
    }
    @Test void databaseErrorsAre500NeverZeroAndNeverExposeSql() throws Exception {
        roles("ADMIN");
        when(query.query(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("secret SQL"));
        mvc.perform(get(url).with(jwt())).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Dashboard metrics could not be loaded."));
    }
}
