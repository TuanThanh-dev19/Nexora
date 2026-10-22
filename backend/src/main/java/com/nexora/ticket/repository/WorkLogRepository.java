package com.nexora.ticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.ticket.entity.WorkLog;

public interface WorkLogRepository extends JpaRepository<WorkLog, Long> {

    @Query("select w from WorkLog w where w.ticket.id = :ticketId order by w.createdAt asc, w.id asc")
    List<WorkLog> findByTicketIdOrderByCreatedAtAscIdAsc(@Param("ticketId") Long ticketId);

    @Query("select case when count(w) > 0 then true else false end from WorkLog w where w.ticket.id = :ticketId")
    boolean existsByTicketId(@Param("ticketId") Long ticketId);
}
