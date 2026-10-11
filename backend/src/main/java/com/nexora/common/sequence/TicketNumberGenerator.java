package com.nexora.common.sequence;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Allocates globally unique numbers; allocation is not rolled back with a ticket. */
@Component
public class TicketNumberGenerator {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bangkok");
    private final JdbcTemplate jdbcTemplate;

    public TicketNumberGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Pass the same creation instant that will be persisted on the ticket. */
    public String nextNumber(OffsetDateTime createdAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        Long sequence = jdbcTemplate.queryForObject(
                "SELECT NEXT VALUE FOR dbo.ticket_number_sequence", Long.class);
        if (sequence == null || sequence <= 0) {
            throw new IllegalStateException("Ticket number sequence must be positive");
        }
        int year = createdAt.atZoneSameInstant(BUSINESS_ZONE).getYear();
        return String.format(Locale.ROOT, "NX-%04d-%06d", year, sequence);
    }
}
