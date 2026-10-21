package com.nexora.ticket;

import com.nexora.ticket.testsupport.TicketCreateApiContract;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.open-in-view=false",
            "spring.datasource.url=jdbc:h2:mem:ticket-create-api;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE"
                + " SCHEMA IF NOT EXISTS dbo"
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TicketCreateApiIntegrationTest extends TicketCreateApiContract {}
