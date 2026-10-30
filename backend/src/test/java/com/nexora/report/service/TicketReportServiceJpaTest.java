package com.nexora.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.common.domain.Priority;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.report.dto.TicketReportExportQuery;
import com.nexora.report.dto.TicketReportQuery;
import com.nexora.report.dto.TicketReportResponse;
import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.service.TicketResponseMapper;
import com.nexora.ticket.testsupport.TicketReadFixtures;

import jakarta.persistence.EntityManager;

@NexoraJpaTest
@Transactional
class TicketReportServiceJpaTest {

    @Autowired
    private EntityManager em;

    private TicketReadFixtures f;
    private TicketReportService service;
    private final OffsetDateTime generatedAt = OffsetDateTime.parse("2026-10-15T12:00:00+07:00");
    private final ZoneId bangkokZone = ZoneId.of("Asia/Bangkok");

    @BeforeEach
    void setup() {
        f = new TicketReadFixtures(em).create();
        service = new TicketReportService(
                em,
                Clock.fixed(generatedAt.toInstant(), bangkokZone),
                new TicketResponseMapper(),
                new TicketCsvExporter()
        );
    }

    private void updateTicket(Ticket ticket, String status, Priority priority, OffsetDateTime created, OffsetDateTime due) {
        em.createNativeQuery("update dbo.tickets set ticket_status=:status, priority=:priority, created_at=:created, sla_due_at=:due where id=:id")
                .setParameter("status", status)
                .setParameter("priority", priority.name())
                .setParameter("created", created)
                .setParameter("due", due)
                .setParameter("id", ticket.getId())
                .executeUpdate();
        em.clear();
    }

    @Test
    @DisplayName("halfOpenIntervalAndEdgeDates: ticket created exactly at 00:00:00 start day is included, end of toDate is included, next day is excluded")
    void halfOpenIntervalAndEdgeDates() {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 7);

        // Exactly at start of fromDate (2026-10-01 00:00:00+07:00)
        updateTicket(f.own, "SUBMITTED", Priority.NORMAL, OffsetDateTime.parse("2026-10-01T00:00:00+07:00"), null);
        // Late inside toDate (2026-10-07 23:59:59+07:00)
        updateTicket(f.current, "IN_PROGRESS", Priority.HIGH, OffsetDateTime.parse("2026-10-07T23:59:59+07:00"), null);
        // Just before fromDate (2026-09-30 23:59:59+07:00)
        updateTicket(f.historical, "RESOLVED", Priority.LOW, OffsetDateTime.parse("2026-09-30T23:59:59+07:00"), null);
        // Just after toDate (2026-10-08 00:00:00+07:00)
        updateTicket(f.hidden, "CLOSED", Priority.NORMAL, OffsetDateTime.parse("2026-10-08T00:00:00+07:00"), null);

        TicketReportQuery query = new TicketReportQuery(
                fromDate,
                toDate,
                null,
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );

        TicketReportResponse response = service.getReport(query);

        assertThat(response.summary().totalTickets()).isEqualTo(2);
        assertThat(response.summary().statusCounts().get(TicketStatus.SUBMITTED)).isEqualTo(1);
        assertThat(response.summary().statusCounts().get(TicketStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(response.summary().statusCounts().get(TicketStatus.RESOLVED)).isEqualTo(0);
        assertThat(response.summary().statusCounts().get(TicketStatus.CLOSED)).isEqualTo(0);
        assertThat(response.tickets().totalElements()).isEqualTo(2);
        assertThat(response.tickets().content()).hasSize(2);
        // Ordered createdAt DESC, id ASC: f.current was created on 10-07, f.own on 10-01
        assertThat(response.tickets().content().get(0).id()).isEqualTo(f.current.getId());
        assertThat(response.tickets().content().get(1).id()).isEqualTo(f.own.getId());
    }

    @Test
    @DisplayName("singleDayInterval: fromDate equals toDate includes only that entire day")
    void singleDayInterval() {
        LocalDate date = LocalDate.of(2026, 10, 5);

        updateTicket(f.own, "SUBMITTED", Priority.NORMAL, OffsetDateTime.parse("2026-10-05T08:00:00+07:00"), null);
        updateTicket(f.current, "ASSIGNED", Priority.NORMAL, OffsetDateTime.parse("2026-10-05T20:00:00+07:00"), null);
        updateTicket(f.historical, "RESOLVED", Priority.LOW, OffsetDateTime.parse("2026-10-04T23:59:59+07:00"), null);

        TicketReportQuery query = new TicketReportQuery(
                date,
                date,
                null,
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );

        TicketReportResponse response = service.getReport(query);

        assertThat(response.summary().totalTickets()).isEqualTo(2);
        assertThat(response.tickets().totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("overdueCalculation: active status with dueAt < generatedAt is overdue; equal or greater is not; resolved/closed is not")
    void overdueCalculation() {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 10);

        // Active ticket overdue (dueAt < generatedAt)
        updateTicket(f.own, "IN_PROGRESS", Priority.HIGH,
                OffsetDateTime.parse("2026-10-02T10:00:00+07:00"),
                generatedAt.minusHours(1));

        // Active ticket with dueAt exactly equal generatedAt -> NOT overdue (< generatedAt required)
        updateTicket(f.current, "SUBMITTED", Priority.HIGH,
                OffsetDateTime.parse("2026-10-02T11:00:00+07:00"),
                generatedAt);

        // Active ticket due in future -> NOT overdue
        updateTicket(f.historical, "ASSIGNED", Priority.NORMAL,
                OffsetDateTime.parse("2026-10-03T10:00:00+07:00"),
                generatedAt.plusHours(5));

        // Inactive ticket (RESOLVED) with dueAt < generatedAt -> NOT counted as overdue
        updateTicket(f.hidden, "RESOLVED", Priority.NORMAL,
                OffsetDateTime.parse("2026-10-04T10:00:00+07:00"),
                generatedAt.minusDays(1));

        TicketReportQuery query = new TicketReportQuery(
                fromDate,
                toDate,
                null,
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );

        TicketReportResponse response = service.getReport(query);

        assertThat(response.summary().totalTickets()).isEqualTo(4);
        assertThat(response.summary().overdueTickets()).isEqualTo(1);
    }

    @Test
    @DisplayName("statusCountsAndTotalSum: every status is present in map and total equals sum of statuses")
    void statusCountsAndTotalSum() {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 10);

        updateTicket(f.own, "SUBMITTED", Priority.NORMAL, OffsetDateTime.parse("2026-10-02T10:00:00+07:00"), null);
        updateTicket(f.current, "UNDER_REVIEW", Priority.HIGH, OffsetDateTime.parse("2026-10-02T11:00:00+07:00"), null);
        updateTicket(f.historical, "RESOLVED", Priority.LOW, OffsetDateTime.parse("2026-10-03T10:00:00+07:00"), null);
        updateTicket(f.hidden, "REJECTED", Priority.NORMAL, OffsetDateTime.parse("2026-10-04T10:00:00+07:00"), null);

        TicketReportQuery query = new TicketReportQuery(
                fromDate,
                toDate,
                null,
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );

        TicketReportResponse response = service.getReport(query);

        assertThat(response.summary().totalTickets()).isEqualTo(4);
        assertThat(response.summary().statusCounts()).hasSize(TicketStatus.values().length);

        long sumOfStatuses = response.summary().statusCounts().values().stream().mapToLong(Long::longValue).sum();
        assertThat(sumOfStatuses).isEqualTo(response.summary().totalTickets());

        assertThat(response.summary().statusCounts().get(TicketStatus.SUBMITTED)).isEqualTo(1);
        assertThat(response.summary().statusCounts().get(TicketStatus.UNDER_REVIEW)).isEqualTo(1);
        assertThat(response.summary().statusCounts().get(TicketStatus.RESOLVED)).isEqualTo(1);
        assertThat(response.summary().statusCounts().get(TicketStatus.REJECTED)).isEqualTo(1);
        assertThat(response.summary().statusCounts().get(TicketStatus.CANCELLED)).isEqualTo(0);
    }

    @Test
    @DisplayName("filtersByLocationAndPriority: filters restrict both summary and ticket list")
    void filtersByLocationAndPriority() {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 10);

        updateTicket(f.own, "SUBMITTED", Priority.HIGH, OffsetDateTime.parse("2026-10-02T10:00:00+07:00"), null);
        updateTicket(f.current, "SUBMITTED", Priority.LOW, OffsetDateTime.parse("2026-10-02T11:00:00+07:00"), null);

        // Filter by Priority HIGH
        TicketReportQuery query = new TicketReportQuery(
                fromDate,
                toDate,
                f.location.getId(),
                Priority.HIGH,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );

        TicketReportResponse response = service.getReport(query);

        assertThat(response.summary().totalTickets()).isEqualTo(1);
        assertThat(response.tickets().totalElements()).isEqualTo(1);
        assertThat(response.tickets().content().get(0).id()).isEqualTo(f.own.getId());
    }

    @Test
    @DisplayName("emptyData: returns zero counts and empty content")
    void emptyData() {
        LocalDate fromDate = LocalDate.of(2026, 1, 1);
        LocalDate toDate = LocalDate.of(2026, 1, 7);

        TicketReportQuery query = new TicketReportQuery(
                fromDate,
                toDate,
                null,
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")))
        );

        TicketReportResponse response = service.getReport(query);

        assertThat(response.summary().totalTickets()).isEqualTo(0);
        assertThat(response.summary().overdueTickets()).isEqualTo(0);
        for (TicketStatus status : TicketStatus.values()) {
            assertThat(response.summary().statusCounts().get(status)).isEqualTo(0L);
        }
        assertThat(response.tickets().totalElements()).isEqualTo(0);
        assertThat(response.tickets().content()).isEmpty();
        assertThat(response.tickets().totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("exportReport: generates CSV with BOM and correct filename")
    void exportReport() {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 10);

        updateTicket(f.own, "SUBMITTED", Priority.NORMAL, OffsetDateTime.parse("2026-10-02T10:00:00+07:00"), null);

        TicketReportExportQuery query = new TicketReportExportQuery(fromDate, toDate, null, null);
        TicketReportService.ExportResult result = service.exportReport(query);

        assertThat(result.filename()).startsWith("ticket-report_2026-10-01_to_2026-10-10_");
        assertThat(result.filename()).endsWith("_ICT.csv");
        assertThat(result.content()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);

        String csv = new String(result.content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Mã phiếu,Tiêu đề");
        assertThat(csv).contains(f.own.getTicketNumber());
    }
}

