package com.nexora.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nexora.dashboard.dto.DashboardSummaryResponse;

class DashboardSummaryResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesTheDashboardContract() throws Exception {
        DashboardSummaryResponse response = new DashboardSummaryResponse(
                12,
                4,
                2,
                7,
                OffsetDateTime.parse("2026-10-09T10:30:00+07:00"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.fieldNames()).toIterable().containsExactly(
                "openTickets",
                "inProgressTickets",
                "slaAtRiskTickets",
                "resolvedThisWeek",
                "generatedAt");
        assertThat(json.get("openTickets").asLong()).isEqualTo(12);
        assertThat(json.get("inProgressTickets").asLong()).isEqualTo(4);
        assertThat(json.get("slaAtRiskTickets").asLong()).isEqualTo(2);
        assertThat(json.get("resolvedThisWeek").asLong()).isEqualTo(7);
        assertThat(json.get("generatedAt").asText()).isEqualTo("2026-10-09T10:30:00+07:00");
    }
}
