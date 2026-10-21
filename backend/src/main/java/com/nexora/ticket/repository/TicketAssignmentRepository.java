package com.nexora.ticket.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.ticket.entity.TicketAssignment;

public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, Long> {

    @Query("select count(a) from TicketAssignment a where a.technicianId = :technicianId and a.endedAt is null")
    long countActiveAssignmentsByTechnicianId(@Param("technicianId") Long technicianId);

    @Query("""
            select a.technicianId, count(a)
            from TicketAssignment a
            where a.technicianId in :technicianIds and a.endedAt is null
            group by a.technicianId
            """)
    List<Object[]> countActiveAssignmentsByTechnicianIds(@Param("technicianIds") Collection<Long> technicianIds);

    Optional<TicketAssignment> findByTicketIdAndEndedAtIsNull(Long ticketId);

    List<TicketAssignment> findByTicketIdInAndEndedAtIsNull(Collection<Long> ticketIds);

    boolean existsByTicketIdAndEndedAtIsNull(Long ticketId);

    boolean existsByTicketIdAndTechnicianId(Long ticketId, Long technicianId);

    List<TicketAssignment> findByTicketIdOrderByAssignedAtDescIdDesc(Long ticketId);
}
