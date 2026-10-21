package com.nexora.dashboard;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.nexora.dashboard.application.DashboardViewer;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.repository.TicketDashboardSummaryQuery;
import com.nexora.ticket.testsupport.TicketReadFixtures;
import com.nexora.user.entity.RoleCode;
import jakarta.persistence.EntityManager;

/** Same aggregation scenarios run on H2 locally and real SQL Server in CI. */
@org.springframework.transaction.annotation.Transactional
public abstract class DashboardQueryContract {
    @Autowired protected EntityManager em;
    protected TicketReadFixtures f;
    protected TicketDashboardSummaryQuery query;
    protected final OffsetDateTime now = OffsetDateTime.parse("2026-10-15T10:30:00+07:00");
    @BeforeEach void setup() {
        f = new TicketReadFixtures(em).create();
        query = new TicketDashboardSummaryQuery(em, Clock.fixed(now.toInstant(), ZoneId.of("America/Los_Angeles")));
    }
    protected DashboardViewer viewer(long id, RoleCode... roles) { return new DashboardViewer(id, Set.of(roles)); }
    protected void state(Ticket ticket, String status, OffsetDateTime due, OffsetDateTime resolved) {
        em.createNativeQuery("update dbo.tickets set ticket_status=:status, sla_due_at=:due, resolved_at=:resolved where id=:id")
                .setParameter("status", status).setParameter("due", due).setParameter("resolved", resolved)
                .setParameter("id", ticket.getId()).executeUpdate();
        em.clear();
    }
    @Test void metricsUseOneBangkokInstantAndClosedStillCountsResolved() {
        state(f.own, "SUBMITTED", now.plusHours(1), null);
        state(f.current, "IN_PROGRESS", now.minusHours(1), null);
        state(f.historical, "RESOLVED", now.minusHours(5), now.minusDays(1));
        state(f.hidden, "CLOSED", now.minusHours(5), now.minusDays(1));
        var result = query.query(viewer(f.manager.getId(), RoleCode.MANAGER));
        assertThat(result.openTickets()).isEqualTo(2);
        assertThat(result.inProgressTickets()).isEqualTo(1);
        assertThat(result.slaAtRiskTickets()).isEqualTo(2);
        assertThat(result.resolvedThisWeek()).isEqualTo(2);
        assertThat(result.generatedAt()).isEqualTo(now);
        assertThat(result.generatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
    }
    @Test void currentAndHistoricalAssignmentsAndMixedRolesAreDeduplicated() {
        em.createNativeQuery("insert into dbo.ticket_assignments (ticket_id, technician_id, assigned_by, assigned_at, ended_at) "
                + "values (:ticket, :technician, :manager, :start, :end)")
                .setParameter("ticket", f.historical.getId()).setParameter("technician", f.multi.getId())
                .setParameter("manager", f.manager.getId()).setParameter("start", now.minusDays(2))
                .setParameter("end", now.minusDays(1)).executeUpdate();
        assertThat(query.query(viewer(f.requester.getId(), RoleCode.REQUESTER)).openTickets()).isEqualTo(1);
        assertThat(query.query(viewer(f.technician.getId(), RoleCode.TECHNICIAN)).openTickets()).isEqualTo(2);
        assertThat(query.query(viewer(f.multi.getId(), RoleCode.REQUESTER, RoleCode.TECHNICIAN)).openTickets()).isEqualTo(2);
        assertThat(query.query(viewer(f.admin.getId(), RoleCode.ADMIN)).openTickets()).isEqualTo(4);
        assertThat(query.query(viewer(f.manager.getId(), RoleCode.MANAGER)).openTickets()).isEqualTo(4);
    }
    @Test void exactSlaBoundaryAndIsoWeekHalfOpenInterval() {
        var monday = OffsetDateTime.parse("2026-10-12T00:00:00+07:00");
        state(f.own, "REOPENED", now.plusHours(2), null);
        state(f.current, "ASSIGNED", now.plusHours(2).plusNanos(100000000), null);
        state(f.historical, "UNDER_REVIEW", null, null);
        state(f.hidden, "CANCELLED", now.minusHours(10), null);
        var result = query.query(viewer(f.admin.getId(), RoleCode.ADMIN));
        assertThat(result.openTickets()).isEqualTo(3);
        assertThat(result.slaAtRiskTickets()).isEqualTo(1);
        state(f.own, "RESOLVED", now, monday);
        state(f.current, "CLOSED", now, monday.minusSeconds(1));
        state(f.historical, "RESOLVED", null, monday.plusWeeks(1));
        result = query.query(viewer(f.admin.getId(), RoleCode.ADMIN));
        assertThat(result.resolvedThisWeek()).isEqualTo(1);
    }
    @Test void emptyScopeReturnsFourZerosWithRealTimestamp() {
        var result = query.query(viewer(f.noRole.getId(), RoleCode.REQUESTER));
        assertThat(result.openTickets()).isZero();
        assertThat(result.inProgressTickets()).isZero();
        assertThat(result.slaAtRiskTickets()).isZero();
        assertThat(result.resolvedThisWeek()).isZero();
        assertThat(result.generatedAt()).isEqualTo(now);
    }
    @Test void changesAreVisibleAndClosingPreservesResolvedMetric() {
        var viewer = viewer(f.requester.getId(), RoleCode.REQUESTER);
        assertThat(query.query(viewer).openTickets()).isEqualTo(1);
        state(f.own, "IN_PROGRESS", now, null);
        assertThat(query.query(viewer).inProgressTickets()).isEqualTo(1);
        state(f.own, "RESOLVED", now, now);
        assertThat(query.query(viewer).openTickets()).isZero();
        assertThat(query.query(viewer).resolvedThisWeek()).isEqualTo(1);
        state(f.own, "CLOSED", now, now);
        assertThat(query.query(viewer).resolvedThisWeek()).isEqualTo(1);
    }
}
