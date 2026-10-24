package com.nexora.ticket.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nexora.auth.service.AuthService;
import com.nexora.common.exception.BusinessRuleException;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.storage.FileStorageService;
import com.nexora.common.storage.FileStorageTransactionCleanup;
import com.nexora.common.storage.FileStorageValidator;
import com.nexora.ticket.dto.response.TicketDetailResponse.AttachmentMetadata;
import com.nexora.ticket.entity.AttachmentType;
import com.nexora.ticket.entity.TicketAttachment;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.ticket.repository.TicketAssignmentRepository;
import com.nexora.ticket.repository.TicketAttachmentRepository;
import com.nexora.ticket.repository.TicketRepository;
import com.nexora.ticket.repository.TicketSpecifications;
import com.nexora.user.entity.AppUser;
import com.nexora.user.entity.RoleCode;

import jakarta.persistence.EntityManager;

@Service
public class TicketAttachmentUploadService {
    private final AuthService auth;
    private final TicketRepository tickets;
    private final TicketAssignmentRepository assignments;
    private final TicketAttachmentRepository attachments;
    private final FileStorageValidator validator;
    private final FileStorageService storage;
    private final FileStorageTransactionCleanup cleanup;
    private final TicketResponseMapper mapper;
    private final EntityManager em;

    public TicketAttachmentUploadService(AuthService auth, TicketRepository tickets,
            TicketAssignmentRepository assignments, TicketAttachmentRepository attachments,
            FileStorageValidator validator, FileStorageService storage,
            FileStorageTransactionCleanup cleanup, TicketResponseMapper mapper, EntityManager em) {
        this.auth = auth;
        this.tickets = tickets;
        this.assignments = assignments;
        this.attachments = attachments;
        this.validator = validator;
        this.storage = storage;
        this.cleanup = cleanup;
        this.mapper = mapper;
        this.em = em;
    }

    @Transactional
    public List<AttachmentMetadata> upload(Jwt jwt, Long ticketId, List<MultipartFile> files) {
        var viewer = auth.getCurrentUser(jwt);
        Set<RoleCode> roles = viewer.roles().stream().map(RoleCode::valueOf).collect(Collectors.toSet());
        // Resolve and assignment changes take the same lock. Hold it through file writes and commit.
        var ticket = tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested ticket is unavailable."));
        if (!tickets.exists(TicketSpecifications.visibleTo(viewer.id(), roles)
                .and(TicketSpecifications.id(ticketId))))
            throw new ResourceNotFoundException("The requested ticket is unavailable.");
        var assignment = assignments.findByTicketIdAndEndedAtIsNull(ticketId);
        if (!roles.contains(RoleCode.TECHNICIAN) || assignment.isEmpty()
                || !assignment.get().getTechnicianId().equals(viewer.id()))
            throw new AccessDeniedException("Only the current assigned technician can upload result images.");
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS)
            throw new BusinessRuleException("Ticket must be IN_PROGRESS to upload result images.");

        validator.validateCount(files);
        for (var file : files) {
            validator.validateSignature(file);
            if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    || file.getOriginalFilename().length() > 255)
                throw TicketSubmissionException.invalid("files", "Image filename must contain 1–255 characters.");
        }

        var uploader = em.getReference(AppUser.class, viewer.id());
        var createdAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        var created = new ArrayList<TicketAttachment>();
        for (var file : files) {
            var stored = storage.store(file);
            cleanup.deleteOnRollback(stored.storageKey());
            created.add(attachments.save(new TicketAttachment(ticket, null, uploader, AttachmentType.WORK_RESULT,
                    stored.storageKey(), stored.originalFilename(), stored.contentType(), stored.size(), createdAt)));
        }
        attachments.flush();
        return created.stream().map(mapper::attachment).toList();
    }
}
