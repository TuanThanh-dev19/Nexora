package com.nexora.ticket.repository;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.nexora.dashboard.application.DashboardViewer;
import com.nexora.dashboard.dto.DashboardSummaryResponse;
import com.nexora.dashboard.port.DashboardSummaryQuery;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

@Repository
@Transactional(readOnly = true)
public class TicketDashboardSummaryQuery implements DashboardSummaryQuery {
    private final EntityManager em;
    private final Clock clock;
    public TicketDashboardSummaryQuery(EntityManager em, Clock clock) { this.em = em; this.clock = clock; }

    @Override
    public DashboardSummaryResponse query(DashboardViewer viewer) {
        var now = clock.instant().atZone(ZoneId.of("Asia/Bangkok")).toOffsetDateTime();
        var weekStart = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneId.of("Asia/Bangkok")).toOffsetDateTime();
        var cb = em.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var ticket = query.from(Ticket.class);
        var active = ticket.get("status").in(List.of(TicketStatus.SUBMITTED, TicketStatus.UNDER_REVIEW,
                TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.REOPENED));
        var inProgress = cb.equal(ticket.get("status"), TicketStatus.IN_PROGRESS);
        var atRisk = cb.and(active, cb.isNotNull(ticket.get("slaDueAt")),
                cb.lessThanOrEqualTo(ticket.<OffsetDateTime>get("slaDueAt"), now.plusHours(2)));
        var resolved = cb.and(cb.greaterThanOrEqualTo(ticket.<OffsetDateTime>get("resolvedAt"), weekStart),
                cb.lessThan(ticket.<OffsetDateTime>get("resolvedAt"), weekStart.plusWeeks(1)));
        query.multiselect(
                cb.sum(cb.<Long>selectCase().when(active, 1L).otherwise(0L)),
                cb.sum(cb.<Long>selectCase().when(inProgress, 1L).otherwise(0L)),
                cb.sum(cb.<Long>selectCase().when(atRisk, 1L).otherwise(0L)),
                cb.sum(cb.<Long>selectCase().when(resolved, 1L).otherwise(0L)));
        // Reuse the exact EXISTS-based visibility policy used by Ticket Detail/list.
        // No joins on assignment history, no entity loading, one aggregate statement.
        query.where(TicketSpecifications.visibleTo(viewer.userId(), viewer.roles()).toPredicate(ticket, query, cb));
        Tuple counts = em.createQuery(query).getSingleResult();
        return new DashboardSummaryResponse(count(counts, 0), count(counts, 1), count(counts, 2), count(counts, 3), now);
    }
    private static long count(Tuple row, int index) {
        Number number = (Number) row.get(index);
        return number == null ? 0 : number.longValue();
    }
}
