package com.nexora.ticket.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import com.nexora.auth.dto.response.CurrentUserResponse;
import com.nexora.auth.service.AuthService;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.storage.*;
import com.nexora.ticket.entity.*;
import com.nexora.ticket.repository.*;

class TicketAttachmentContentServiceTest {
    final AuthService auth = mock(AuthService.class);
    final TicketRepository tickets = mock(TicketRepository.class);
    final TicketAttachmentRepository attachments = mock(TicketAttachmentRepository.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final TicketAttachmentContentService service = new TicketAttachmentContentService(auth, tickets, attachments, storage);
    final Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject("1").build();

    void visible() {
        when(auth.getCurrentUser(jwt)).thenReturn(new CurrentUserResponse(1L, "test@example.test", "Test", "ACTIVE", List.of("REQUESTER")));
        when(tickets.findOne(any(Specification.class))).thenReturn(Optional.of(mock(Ticket.class)));
    }
    TicketAttachment metadata(String mime, AttachmentType type) {
        var attachment = mock(TicketAttachment.class);
        when(attachment.getContentType()).thenReturn(mime);
        when(attachment.getAttachmentType()).thenReturn(type);
        when(attachment.getStorageKey()).thenReturn("private-key");
        when(attachment.getOriginalName()).thenReturn("../ảnh\r\n.png");
        when(attachments.findByTicketIdAndId(10L, 20L)).thenReturn(Optional.of(attachment));
        return attachment;
    }
    @ParameterizedTest @ValueSource(strings = {"image/jpeg", "image/png"})
    void readsActualBytesAndSanitizesFilename(String mime) {
        visible(); metadata(mime, AttachmentType.WORK_RESULT);
        when(storage.open("private-key")).thenReturn(new ByteArrayResource(new byte[]{1, 2, 3}));
        var content = service.read(jwt, 10L, 20L);
        assertThat(content.bytes()).containsExactly(1, 2, 3);
        assertThat(content.contentType()).isEqualTo(mime);
        assertThat(content.filename()).doesNotContain("/", "\\", "\r", "\n", "..");
    }
    @Test void invisibleParentDoesNotReadMetadataOrStorage() {
        visible(); when(tickets.findOne(any(Specification.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(attachments, storage);
    }
    @Test void missingOrWrongParentAttachmentDoesNotOpenStorage() {
        visible();
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        verify(attachments).findByTicketIdAndId(10L, 20L);
        verifyNoInteractions(storage);
    }
    @Test void noRoleDoesNotReadResources() {
        when(auth.getCurrentUser(jwt)).thenReturn(new CurrentUserResponse(1L, "test@example.test", "Test", "ACTIVE", List.of()));
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tickets, attachments, storage);
    }
    @Test void commentsAndUnsupportedMimeAreHidden() {
        visible(); metadata("image/png", AttachmentType.COMMENT);
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        metadata("text/html", AttachmentType.REPORT_EVIDENCE);
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }
    @Test void missingFileIs404ButIoFailureIsNotHidden() throws Exception {
        visible(); metadata("image/png", AttachmentType.REPORT_EVIDENCE);
        when(storage.open("private-key")).thenThrow(new StoredFileNotFoundException("private-key"));
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(ResourceNotFoundException.class);
        var resource = mock(Resource.class);
        doReturn(resource).when(storage).open("private-key");
        when(resource.getInputStream()).thenThrow(new IOException("secret path"));
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(FileStorageException.class)
                .hasMessage("Could not read attachment.");
    }
    @Test void closesStreamAndRejectsEmptyFile() throws Exception {
        visible(); metadata("image/png", AttachmentType.REPORT_EVIDENCE);
        var resource = mock(Resource.class);
        var stream = spy(new java.io.ByteArrayInputStream(new byte[0]));
        when(resource.getInputStream()).thenReturn(stream);
        when(storage.open("private-key")).thenReturn(resource);
        assertThatThrownBy(() -> service.read(jwt, 10L, 20L)).isInstanceOf(FileStorageException.class);
        verify(stream).close();
    }
}
