package com.nexora;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.persistence.SqlReferenceValidator;
import com.nexora.notification.repository.NotificationRepository;
import com.nexora.notification.service.NotificationService;
import com.nexora.ticket.repository.TicketCommentRepository;
import com.nexora.ticket.service.TicketCommentService;

class DocumentReferenceValidationTest {

    @Test
    void commentChecksSqlTicketAndAuthorBeforeSaving() {
        TicketCommentRepository repository = mock(TicketCommentRepository.class);
        SqlReferenceValidator validator = mock(SqlReferenceValidator.class);
        TicketCommentService service = new TicketCommentService(repository, validator);

        service.create(10L, 20L, "Valid comment", "PUBLIC");

        verify(validator).requireTicket(10L);
        verify(validator).requireUser(20L);
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void notificationChecksSqlRecipientAndOptionalTicketBeforeSaving() {
        NotificationRepository repository = mock(NotificationRepository.class);
        SqlReferenceValidator validator = mock(SqlReferenceValidator.class);
        NotificationService service = new NotificationService(repository, validator);

        service.create(20L, 10L, "TICKET_UPDATED", "Updated", "Ticket changed");

        verify(validator).requireUser(20L);
        verify(validator).requireTicket(10L);
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markReadRejectsUnknownNotification() {
        NotificationRepository repository = mock(NotificationRepository.class);
        when(repository.findById("missing")).thenReturn(Optional.empty());
        NotificationService service = new NotificationService(
                repository, mock(SqlReferenceValidator.class));

        assertThatThrownBy(() -> service.markRead("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
