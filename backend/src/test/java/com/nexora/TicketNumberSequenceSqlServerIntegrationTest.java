package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.nexora.common.sequence.TicketNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class TicketNumberSequenceSqlServerIntegrationTest {
    @Autowired private TicketNumberGenerator generator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    private final OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-11T00:00:00Z");

    @Test
    void freshFlywayMigrationCreatesNonCyclingBigintSequenceAndKeepsUniqueConstraint() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version = '10' AND success = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.sequences WHERE name = 'ticket_number_sequence' AND TYPE_NAME(system_type_id) = 'bigint' AND is_cycling = 0 AND increment = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.tickets') AND name = 'uq_tickets_ticket_number' AND is_unique = 1 AND is_disabled = 0", Integer.class)).isEqualTo(1);
    }

    @Test
    void consecutiveAllocationsIncreaseAcrossCalendarYearsAndRollbackCreatesGap() {
        long first = suffix(generator.nextNumber(createdAt));
        var nextYear = generator.nextNumber(createdAt.plusYears(1));
        assertThat(nextYear).startsWith("NX-2027-");
        assertThat(suffix(nextYear)).isEqualTo(first + 1);
        var transaction = new TransactionTemplate(transactions);
        long rolledBack = transaction.execute(status -> {
            long value = suffix(generator.nextNumber(createdAt));
            status.setRollbackOnly();
            return value;
        });
        assertThat(suffix(generator.nextNumber(createdAt))).isEqualTo(rolledBack + 1);
    }

    @Test
    void concurrentJdbcAllocationsNeverDuplicate() throws Exception {
        var tasks = new ArrayList<Callable<String>>();
        for (int i = 0; i < 1000; i++) tasks.add(() -> generator.nextNumber(createdAt));
        var numbers = new HashSet<String>();
        try (var executor = Executors.newFixedThreadPool(16)) {
            for (var result : executor.invokeAll(tasks)) {
                String number = result.get();
                assertThat(number).matches("NX-2026-[0-9]{6,19}");
                assertThat(numbers.add(number)).isTrue();
            }
        }
        assertThat(numbers).hasSize(1000);
    }

    private long suffix(String number) { return Long.parseLong(number.substring(8)); }
}
