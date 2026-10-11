package com.nexora.common.sequence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TicketNumberGeneratorTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TicketNumberGenerator generator = new TicketNumberGenerator(jdbc);

    @Test
    void usesBangkokYearAtUtcNewYearBoundaryAndAsciiPadding() {
        when(jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.ticket_number_sequence", Long.class))
                .thenReturn(123L);
        assertThat(generator.nextNumber(OffsetDateTime.parse("2025-12-31T17:00:00Z")))
                .isEqualTo("NX-2026-000123");
        assertThat(generator.nextNumber(OffsetDateTime.parse("2025-12-31T16:59:59Z")))
                .isEqualTo("NX-2025-000123");
    }

    @Test
    void neverTruncatesSequenceAboveSixDigits() {
        when(jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.ticket_number_sequence", Long.class))
                .thenReturn(999999L, 1000000L, 10000000L, Long.MAX_VALUE);
        var instant = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        assertThat(generator.nextNumber(instant)).isEqualTo("NX-2026-999999");
        assertThat(generator.nextNumber(instant)).isEqualTo("NX-2026-1000000");
        assertThat(generator.nextNumber(instant)).isEqualTo("NX-2026-10000000");
        assertThat(generator.nextNumber(instant)).isEqualTo("NX-2026-9223372036854775807").hasSize(27);
    }

    @Test
    void rejectsMissingCreationInstantAndInvalidSequence() {
        assertThatThrownBy(() -> generator.nextNumber(null)).isInstanceOf(NullPointerException.class);
        when(jdbc.queryForObject("SELECT NEXT VALUE FOR dbo.ticket_number_sequence", Long.class))
                .thenReturn(null, 0L, -1L);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> generator.nextNumber(OffsetDateTime.now()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
