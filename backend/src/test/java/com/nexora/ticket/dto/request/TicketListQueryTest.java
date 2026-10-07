package com.nexora.ticket.dto.request;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.LinkedMultiValueMap;
import com.nexora.common.exception.*;

class TicketListQueryTest {
    @Test
    void defaultsAndDeterministicSortUseSharedContract() {
        var query = TicketListQuery.from(new LinkedMultiValueMap<>());
        assertThat(query.pageable().getPageNumber()).isZero();
        assertThat(query.pageable().getPageSize()).isEqualTo(20);
        assertThat(query.pageable().getSort().toString()).isEqualTo("createdAt: DESC,id: ASC");
    }
    @ParameterizedTest
    @CsvSource({"page,-1", "page,no", "page,2147483648", "page,2147483647", "size,0", "size,101", "size,1.5",
            "sort,unknown", "sort,'createdAt,sideways'", "sort,'createdAt,desc,extra'",
            "status,submitted", "priority,CRITICAL", "locationId,0", "locationId,-1",
            "locationId,9223372036854775808", "keyword,test"})
    void rejectsInvalidAndUnknownParameters(String key, String value) {
        var params = new LinkedMultiValueMap<String, String>(); params.add(key, value);
        assertThatThrownBy(() -> TicketListQuery.from(params)).isInstanceOfAny(
                InvalidPaginationException.class, UnsupportedSortException.class, UnsupportedFilterException.class);
    }
    @Test
    void blankAndRepeatedParametersAreRejected() {
        for (String key : java.util.List.of("page", "size", "sort", "status", "priority", "locationId")) {
            var params = new LinkedMultiValueMap<String, String>(); params.add(key, "");
            assertThatThrownBy(() -> TicketListQuery.from(params)).isInstanceOf(RuntimeException.class);
            params.add(key, "");
            assertThatThrownBy(() -> TicketListQuery.from(params)).isInstanceOf(RuntimeException.class);
        }
    }
    @Test
    void supportedValuesAndImplicitAscAreParsed() {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "2"); params.add("size", "100"); params.add("sort", "ticketNumber");
        params.add("status", "REOPENED"); params.add("priority", "URGENT"); params.add("locationId", "12");
        var query = TicketListQuery.from(params);
        assertThat(query.pageable().getSort().getOrderFor("ticketNumber").isAscending()).isTrue();
        assertThat(query.locationId()).isEqualTo(12L);
        assertThat(query.status().name()).isEqualTo("REOPENED");
    }
}
