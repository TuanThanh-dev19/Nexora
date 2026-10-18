package com.nexora.ticket.service;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.auth.service.AuthService;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.storage.*;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.repository.*;
import com.nexora.user.entity.RoleCode;

@Service
@Transactional(readOnly = true)
public class TicketAttachmentContentService {
    private final AuthService auth;
    private final TicketRepository tickets;
    private final TicketAttachmentRepository attachments;
    private final FileStorageService storage;

    public TicketAttachmentContentService(AuthService auth, TicketRepository tickets,
            TicketAttachmentRepository attachments, FileStorageService storage) {
        this.auth = auth;
        this.tickets = tickets;
        this.attachments = attachments;
        this.storage = storage;
    }

    public record Content(byte[] bytes, String contentType, String filename) {}

    public Content read(Jwt jwt, Long ticketId, Long attachmentId) {
        var viewer = auth.getCurrentUser(jwt);
        Set<RoleCode> roles = Arrays.stream(RoleCode.values())
                .filter(role -> viewer.roles().contains(role.name())).collect(Collectors.toSet());
        if (roles.isEmpty()) throw new AccessDeniedException("No ticket read role.");
        if (tickets.findOne(TicketSpecifications.visibleTo(viewer.id(), roles)
                .and(TicketSpecifications.id(ticketId))).isEmpty()) throw unavailable();
        var metadata = attachments.findByTicketIdAndId(ticketId, attachmentId).orElseThrow(
                TicketAttachmentContentService::unavailable);
        if (!Set.of(AttachmentType.REPORT_EVIDENCE, AttachmentType.WORK_RESULT).contains(metadata.getAttachmentType())
                || !Set.of("image/jpeg", "image/png").contains(metadata.getContentType())) throw unavailable();
        // Files are capped at 5 MiB on upload. Buffer before writing headers so an I/O
        // error can still produce a complete JSON problem, never a partial 200 image.
        try (var input = storage.open(metadata.getStorageKey()).getInputStream()) {
            byte[] bytes = input.readNBytes(5 * 1024 * 1024 + 1);
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
                throw new FileStorageException("Invalid stored image size.", null);
            }
            String filename = metadata.getOriginalName().replaceAll("[\\p{Cntrl}/\\\\]", "_")
                    .replace("..", "_").replace('"', '_');
            if (filename.isBlank()) filename = "evidence";
            return new Content(bytes, metadata.getContentType(), filename);
        } catch (StoredFileNotFoundException | FileNotFoundException | NoSuchFileException exception) {
            throw unavailable();
        } catch (IOException | InvalidFileException exception) {
            throw new FileStorageException("Could not read attachment.", exception);
        }
    }

    private static ResourceNotFoundException unavailable() {
        return new ResourceNotFoundException("The requested attachment is unavailable.");
    }
}
