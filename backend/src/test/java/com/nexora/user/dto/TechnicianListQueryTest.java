package com.nexora.user.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.nexora.common.exception.InvalidPaginationException;
import com.nexora.common.exception.UnsupportedFilterException;
import com.nexora.common.exception.UnsupportedSortException;

class TechnicianListQueryTest {

    @Test
    @DisplayName("Defaults to page=0, size=20, null filters and deterministic sort by fullName ASC, technicianId ASC")
    void defaultsAndDeterministicSort() {
        TechnicianListQuery query = TechnicianListQuery.from(new LinkedMultiValueMap<>());

        assertThat(query.pageable().getPageNumber()).isZero();
        assertThat(query.pageable().getPageSize()).isEqualTo(20);
        assertThat(query.query()).isNull();
        assertThat(query.available()).isNull();

        var sortOrders = query.pageable().getSort().toList();
        assertThat(sortOrders).hasSize(2);
        assertThat(sortOrders.get(0).getProperty()).isEqualTo("user.fullName");
        assertThat(sortOrders.get(0).isAscending()).isTrue();
        assertThat(sortOrders.get(1).getProperty()).isEqualTo("technicianId");
        assertThat(sortOrders.get(1).isAscending()).isTrue();
    }

    @Test
    @DisplayName("Valid bounds size=1 and size=100 are accepted")
    void validPageAndSizeBounds() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "5");
        params.add("size", "1");
        TechnicianListQuery query1 = TechnicianListQuery.from(params);
        assertThat(query1.pageable().getPageNumber()).isEqualTo(5);
        assertThat(query1.pageable().getPageSize()).isEqualTo(1);

        params.set("size", "100");
        TechnicianListQuery query100 = TechnicianListQuery.from(params);
        assertThat(query100.pageable().getPageSize()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-20", "abc", "1.5", "2147483648"})
    @DisplayName("Negative or malformed page numbers are rejected with 400")
    void invalidPageRejected(String invalidPage) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", invalidPage);

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "200", "xyz", "2.0"})
    @DisplayName("Out-of-range or malformed size values are rejected with 400")
    void invalidSizeRejected(String invalidSize) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("size", invalidSize);

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    @DisplayName("Offset arithmetic overflow page * size > Integer.MAX_VALUE is rejected")
    void offsetOverflowRejected() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", String.valueOf(Integer.MAX_VALUE));
        params.add("size", "20");

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessageContaining("offset exceeds");
    }

    @Test
    @DisplayName("Valid query trims whitespace")
    void validQueryTrimmed() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "   Nguyễn An   ");

        TechnicianListQuery query = TechnicianListQuery.from(params);
        assertThat(query.query()).isEqualTo("Nguyễn An");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Empty or whitespace-only query is rejected with 400")
    void emptyOrWhitespaceQueryRejected(String blankQuery) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", blankQuery);

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(UnsupportedFilterException.class)
                .hasMessageContaining("Query must not be empty or blank");
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,false", "TRUE,true", "FALSE,false", "True,true", "False,false"})
    @DisplayName("Valid boolean available values true and false are accepted")
    void validAvailableBooleans(String input, boolean expected) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("available", input);

        TechnicianListQuery query = TechnicianListQuery.from(params);
        assertThat(query.available()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "yes", "no", "invalid", "", "   "})
    @DisplayName("Invalid boolean available values are rejected with 400")
    void invalidAvailableRejected(String invalidAvailable) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("available", invalidAvailable);

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(UnsupportedFilterException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "query", "available"})
    @DisplayName("Duplicate query parameters are rejected with 400")
    void duplicateParametersRejected(String paramKey) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if ("page".equals(paramKey)) {
            params.addAll(paramKey, List.of("0", "1"));
        } else if ("size".equals(paramKey)) {
            params.addAll(paramKey, List.of("20", "50"));
        } else if ("query".equals(paramKey)) {
            params.addAll(paramKey, List.of("an", "binh"));
        } else {
            params.addAll(paramKey, List.of("true", "false"));
        }

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOfAny(InvalidPaginationException.class, UnsupportedFilterException.class);
    }

    @Test
    @DisplayName("Client-supplied sort is rejected with 400")
    void sortRejected() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("sort", "fullName,asc");

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(UnsupportedSortException.class);
    }

    @Test
    @DisplayName("Unknown query parameters are rejected with 400")
    void unknownParameterRejected() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "all");

        assertThatThrownBy(() -> TechnicianListQuery.from(params))
                .isInstanceOf(UnsupportedFilterException.class)
                .hasMessageContaining("Unsupported query parameter");
    }
}
