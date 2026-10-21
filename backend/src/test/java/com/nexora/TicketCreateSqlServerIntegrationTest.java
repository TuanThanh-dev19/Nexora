package com.nexora;

import com.nexora.ticket.testsupport.TicketCreateApiContract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        properties = {
            "spring.data.mongodb.auto-index-creation=false",
            "spring.jpa.open-in-view=false"
        })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_IT", matches = "(?i)true")
class TicketCreateSqlServerIntegrationTest extends TicketCreateApiContract {}
