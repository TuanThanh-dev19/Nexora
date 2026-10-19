package com.nexora.ticket.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.auth.service.InvalidCredentialsException;
import com.nexora.category.repository.*;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.ticket.repository.*;
import com.nexora.ticket.dto.request.TicketListQuery;
import org.springframework.util.LinkedMultiValueMap;

class TicketReadServiceTest {
    private final AuthService auth = mock(AuthService.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final FieldDefinitionRepository fields = mock(FieldDefinitionRepository.class);
    private final FieldOptionRepository options = mock(FieldOptionRepository.class);
    private final TicketFieldValueRepository values = mock(TicketFieldValueRepository.class);
    private final TicketStatusHistoryRepository history = mock(TicketStatusHistoryRepository.class);
    private final TicketAttachmentRepository attachments = mock(TicketAttachmentRepository.class);
    private final TicketReadService service = new TicketReadService(auth, tickets, fields, options, values,
            history, attachments, new TicketResponseMapper(), mock(TicketAssignmentRepository.class),
            mock(com.nexora.user.repository.AppUserRepository.class));
    private final Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("10").build();

    @Test
    void unavailableTicketNeverLoadsChildrenOrChecksExistenceSeparately() {
        when(auth.getCurrentUser(jwt)).thenReturn(new CurrentUserResponse(10L, "user@example.test", "User", "ACTIVE", List.of("REQUESTER")));
        when(tickets.findOne(any(org.springframework.data.jpa.domain.Specification.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(jwt, 100L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The requested ticket is unavailable.");
        verifyNoInteractions(fields, options, values, history, attachments);
        verify(tickets, never()).findById(any());
    }
    @Test
    void invalidIdentityFromAuthNeverReadsTicketsOrChildren() {
        when(auth.getCurrentUser(jwt)).thenThrow(new BadCredentialsException("Invalid identity."));
        assertThatThrownBy(() -> service.detail(jwt, 1L)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> service.list(jwt, TicketListQuery.from(new LinkedMultiValueMap<>())))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tickets, fields, options, values, history, attachments);
    }

    @Test
    void missingOrInactiveCurrentUserKeepsTicketUnauthorizedConvention() {
        when(auth.getCurrentUser(jwt)).thenThrow(new InvalidCredentialsException());
        assertThatThrownBy(() -> service.detail(jwt, 1L)).isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid identity.");
        assertThatThrownBy(() -> service.list(jwt, TicketListQuery.from(new LinkedMultiValueMap<>())))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tickets, fields, options, values, history, attachments);
    }

    @Test
    void noRolesNeverReadsTicketsOrChildren() {
        when(auth.getCurrentUser(jwt)).thenReturn(new CurrentUserResponse(10L, "user@example.test", "User", "ACTIVE", List.of()));
        assertThatThrownBy(() -> service.detail(jwt, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.list(jwt, TicketListQuery.from(new LinkedMultiValueMap<>())))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tickets, fields, options, values, history, attachments);
    }
}
