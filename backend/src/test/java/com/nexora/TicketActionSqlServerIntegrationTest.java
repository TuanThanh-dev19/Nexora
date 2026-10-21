package com.nexora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import com.nexora.ticket.entity.TicketAssignment;
import com.nexora.ticket.testsupport.TicketActionApiContract;

@SpringBootTest(properties = {"spring.data.mongodb.auto-index-creation=false", "spring.jpa.open-in-view=false"})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class TicketActionSqlServerIntegrationTest extends TicketActionApiContract {
    @Test
    void filteredIndexRejectsASecondActiveAssignmentEvenWithoutTheService() {
        review(f.own);
        assign(f.own);
        assertThatThrownBy(() -> tx(() -> {
            assignments.saveAndFlush(new TicketAssignment(
                    f.own.getId(), f.multi.getId(), f.manager.getId(), OffsetDateTime.now()));
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assignments.findByTicketIdOrderByAssignedAtDescIdDesc(f.own.getId()))
                .hasSize(1);
    }
}
