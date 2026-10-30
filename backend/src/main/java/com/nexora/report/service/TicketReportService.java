package com.nexora.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.common.domain.Priority;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.pagination.PageResponse;
import com.nexora.report.dto.TicketReportExportQuery;
import com.nexora.report.dto.TicketReportQuery;
import com.nexora.report.dto.TicketReportResponse;
import com.nexora.report.dto.TicketReportSummaryResponse;
import com.nexora.ticket.dto.response.TicketSummaryResponse;
import com.nexora.ticket.entity.Ticket;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.service.TicketResponseMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
@Transactional(readOnly = true)
public class TicketReportService {

    public static final ZoneId BANGKOK_ZONE = ZoneId.of("Asia/Bangkok");
    private static final int MAX_EXPORT_LIMIT = 10_000;
    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.SUBMITTED,
            TicketStatus.UNDER_REVIEW,
            TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS,
            TicketStatus.REOPENED
    );

    private final EntityManager em;
    private final Clock clock;
    private final TicketResponseMapper mapper;
    private final TicketCsvExporter csvExporter;

    public TicketReportService(EntityManager em, Clock clock, TicketResponseMapper mapper, TicketCsvExporter csvExporter) {
        this.em = em;
        this.clock = clock;
        this.mapper = mapper;
        this.csvExporter = csvExporter;
    }

    public record ExportResult(String filename, byte[] content) {}

    public TicketReportResponse getReport(TicketReportQuery query) {
        OffsetDateTime generatedAt = clock.instant().atZone(BANGKOK_ZONE).toOffsetDateTime();
        OffsetDateTime start = query.fromDate().atStartOfDay(BANGKOK_ZONE).toOffsetDateTime();
        OffsetDateTime end = query.toDate().plusDays(1).atStartOfDay(BANGKOK_ZONE).toOffsetDateTime();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1. Single aggregate query for summary metrics
        CriteriaQuery<Tuple> summaryQuery = cb.createTupleQuery();
        Root<Ticket> summaryRoot = summaryQuery.from(Ticket.class);

        Predicate filters = buildPredicate(cb, summaryRoot, start, end, query.locationId(), query.priority());

        var activeCondition = summaryRoot.get("status").in(ACTIVE_STATUSES);
        var overdueCondition = cb.and(
                activeCondition,
                cb.isNotNull(summaryRoot.get("slaDueAt")),
                cb.lessThan(summaryRoot.<OffsetDateTime>get("slaDueAt"), generatedAt)
        );

        List<jakarta.persistence.criteria.Selection<?>> selections = new ArrayList<>();
        selections.add(cb.count(summaryRoot)); // index 0: total
        selections.add(cb.sum(cb.<Long>selectCase().when(overdueCondition, 1L).otherwise(0L))); // index 1: overdue

        TicketStatus[] statuses = TicketStatus.values();
        for (TicketStatus status : statuses) {
            selections.add(cb.sum(cb.<Long>selectCase()
                    .when(cb.equal(summaryRoot.get("status"), status), 1L)
                    .otherwise(0L)));
        }

        summaryQuery.multiselect(selections);
        summaryQuery.where(filters);

        Tuple row = em.createQuery(summaryQuery).getSingleResult();
        long totalTickets = getCount(row, 0);
        long overdueTickets = getCount(row, 1);

        Map<TicketStatus, Long> statusCounts = new EnumMap<>(TicketStatus.class);
        for (int i = 0; i < statuses.length; i++) {
            statusCounts.put(statuses[i], getCount(row, 2 + i));
        }

        TicketReportSummaryResponse summaryResponse = new TicketReportSummaryResponse(
                query.fromDate(),
                query.toDate(),
                BANGKOK_ZONE.getId(),
                generatedAt,
                totalTickets,
                overdueTickets,
                statusCounts
        );

        // 2. Query page content
        PageResponse<TicketSummaryResponse> pageResponse;
        if (totalTickets == 0) {
            pageResponse = new PageResponse<>(
                    List.of(),
                    query.pageable().getPageNumber(),
                    query.pageable().getPageSize(),
                    0,
                    0
            );
        } else {
            CriteriaQuery<Ticket> itemQuery = cb.createQuery(Ticket.class);
            Root<Ticket> itemRoot = itemQuery.from(Ticket.class);
            itemQuery.where(buildPredicate(cb, itemRoot, start, end, query.locationId(), query.priority()));
            itemQuery.orderBy(cb.desc(itemRoot.get("createdAt")), cb.asc(itemRoot.get("id")));

            List<Ticket> ticketEntities = em.createQuery(itemQuery)
                    .setFirstResult((int) query.pageable().getOffset())
                    .setMaxResults(query.pageable().getPageSize())
                    .getResultList();

            List<TicketSummaryResponse> content = ticketEntities.stream()
                    .map(mapper::summary)
                    .toList();

            int totalPages = (int) Math.ceil((double) totalTickets / query.pageable().getPageSize());
            pageResponse = new PageResponse<>(
                    content,
                    query.pageable().getPageNumber(),
                    query.pageable().getPageSize(),
                    totalTickets,
                    totalPages
            );
        }

        return new TicketReportResponse(summaryResponse, pageResponse);
    }

    public ExportResult exportReport(TicketReportExportQuery query) {
        OffsetDateTime generatedAt = clock.instant().atZone(BANGKOK_ZONE).toOffsetDateTime();
        OffsetDateTime start = query.fromDate().atStartOfDay(BANGKOK_ZONE).toOffsetDateTime();
        OffsetDateTime end = query.toDate().plusDays(1).atStartOfDay(BANGKOK_ZONE).toOffsetDateTime();

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1. Check total count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Ticket> countRoot = countQuery.from(Ticket.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicate(cb, countRoot, start, end, query.locationId(), query.priority()));

        Long count = em.createQuery(countQuery).getSingleResult();
        long total = count == null ? 0 : count;

        if (total > MAX_EXPORT_LIMIT) {
            throw new BusinessRuleException(
                    "The export query matches " + total + " tickets, exceeding the maximum limit of "
                            + MAX_EXPORT_LIMIT + " tickets. Please narrow your date range or filters.");
        }

        // 2. Fetch all matching tickets in order
        CriteriaQuery<Ticket> dataQuery = cb.createQuery(Ticket.class);
        Root<Ticket> dataRoot = dataQuery.from(Ticket.class);
        dataQuery.where(buildPredicate(cb, dataRoot, start, end, query.locationId(), query.priority()));
        dataQuery.orderBy(cb.desc(dataRoot.get("createdAt")), cb.asc(dataRoot.get("id")));

        List<Ticket> tickets = em.createQuery(dataQuery)
                .setMaxResults(MAX_EXPORT_LIMIT)
                .getResultList();

        List<TicketSummaryResponse> items = tickets.stream().map(mapper::summary).toList();
        byte[] csvBytes = csvExporter.export(items);

        String filename = String.format("ticket-report_%s_to_%s_%s_ICT.csv",
                query.fromDate(),
                query.toDate(),
                generatedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));

        return new ExportResult(filename, csvBytes);
    }

    private static Predicate buildPredicate(
            CriteriaBuilder cb,
            Root<Ticket> root,
            OffsetDateTime start,
            OffsetDateTime end,
            Long locationId,
            Priority priority) {

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        predicates.add(cb.lessThan(root.get("createdAt"), end));

        if (locationId != null) {
            predicates.add(cb.equal(root.get("location").get("id"), locationId));
        }

        if (priority != null) {
            predicates.add(cb.equal(root.get("priority"), priority));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private static long getCount(Tuple row, int index) {
        if (row == null) return 0;
        Number num = (Number) row.get(index);
        return num == null ? 0 : num.longValue();
    }
}

