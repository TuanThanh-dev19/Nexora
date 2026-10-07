package com.nexora.ticket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.LinkedMultiValueMap;
import com.nexora.testsupport.NexoraJpaTest;
import com.nexora.ticket.dto.request.TicketListQuery;
import com.nexora.ticket.testsupport.TicketReadFixtures;
import com.nexora.user.entity.RoleCode;
import jakarta.persistence.EntityManager;

@NexoraJpaTest
class TicketVisibilityRepositoryTest {
    @Autowired EntityManager em;
    @Autowired TicketRepository tickets;
    @Test
    void existsAvoidsDuplicateRowsAndCountWithRepeatedHistoricalAssignments() {
        var f = new TicketReadFixtures(em).create();
        em.clear();
        var params = new LinkedMultiValueMap<String, String>(); params.add("size", "1"); params.add("page", "1");
        var page = tickets.findAll(TicketSpecifications.visibleTo(f.technician.getId(), Set.of(RoleCode.TECHNICIAN)),
                TicketListQuery.from(params).pageable());
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(ticket -> ticket.getId()).containsExactly(f.historical.getId());
        assertThat(tickets.findOne(TicketSpecifications.visibleTo(f.requester.getId(), Set.of(RoleCode.REQUESTER))
                .and(TicketSpecifications.id(f.hidden.getId())))).isEmpty();
        assertThat(tickets.findAll(TicketSpecifications.visibleTo(f.noRole.getId(), Set.of()),
                TicketListQuery.from(new LinkedMultiValueMap<>()).pageable()).getTotalElements()).isZero();
    }
}
