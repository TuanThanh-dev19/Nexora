package com.nexora.ticket.service;

import com.nexora.asset.entity.*;
import com.nexora.auth.service.AuthService;
import com.nexora.category.entity.*;
import com.nexora.category.repository.*;
import com.nexora.common.domain.Priority;
import com.nexora.common.exception.ResourceNotFoundException;
import com.nexora.common.sequence.TicketNumberGenerator;
import com.nexora.common.storage.*;
import com.nexora.location.entity.Location;
import com.nexora.sla.entity.SlaPolicy;
import com.nexora.ticket.dto.request.CreateTicketRequest;
import com.nexora.ticket.dto.response.CreateTicketResponse;
import com.nexora.ticket.entity.*;
import com.nexora.user.entity.*;

import jakarta.persistence.*;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class TicketCreateService {
    private final EntityManager em;
    private final AuthService auth;
    private final IncidentCategoryRepository categories;
    private final FieldDefinitionRepository fields;
    private final FieldOptionRepository options;
    private final TicketSubmissionValidator validator;
    private final TicketNumberGenerator numbers;
    private final FileStorageService storage;
    private final FileStorageTransactionCleanup cleanup;

    public TicketCreateService(
            EntityManager em,
            AuthService auth,
            IncidentCategoryRepository categories,
            FieldDefinitionRepository fields,
            FieldOptionRepository options,
            TicketSubmissionValidator validator,
            TicketNumberGenerator numbers,
            FileStorageService storage,
            FileStorageTransactionCleanup cleanup) {
        this.em = em;
        this.auth = auth;
        this.categories = categories;
        this.fields = fields;
        this.options = options;
        this.validator = validator;
        this.numbers = numbers;
        this.storage = storage;
        this.cleanup = cleanup;
    }

    @Transactional
    public CreateTicketResponse create(
            Jwt jwt, CreateTicketRequest request, List<MultipartFile> files) {
        var identity = auth.getCurrentUser(jwt);
        if (!identity.roles().contains(RoleCode.REQUESTER.name()))
            throw new AccessDeniedException("REQUESTER is required.");
        AppUser reporter = locked(AppUser.class, identity.id());
        if (reporter.getStatus() != UserStatus.ACTIVE)
            throw new com.nexora.auth.service.InvalidCredentialsException();
        validator.validateRequest(request);
        // Publishing takes the same category lock. Hold all reference locks until commit, including
        // file writes.
        IncidentCategory category =
                categories
                        .findByIdForUpdate(request.categoryId())
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
        if (!category.isActive())
            throw TicketSubmissionException.invalid("categoryId", "Category is inactive.");
        CategoryFormVersion form = locked(CategoryFormVersion.class, request.formVersionId());
        if (!form.getCategory().getId().equals(category.getId()))
            throw TicketSubmissionException.invalid(
                    "formVersionId", "The form belongs to another category.");
        if (form.getFormStatus() != FormStatus.PUBLISHED) throw TicketSubmissionException.stale();
        Location location = locked(Location.class, request.locationId());
        if (!location.isActive())
            throw TicketSubmissionException.invalid("locationId", "Location is inactive.");
        if (category.getAssetPolicy() == AssetPolicy.FORBIDDEN && form.isRequiresEquipment())
            throw TicketSubmissionException.incompatible("formVersionId");
        Asset asset = request.assetId() == null ? null : locked(Asset.class, request.assetId());
        if (category.getAssetPolicy() == AssetPolicy.FORBIDDEN && asset != null)
            throw TicketSubmissionException.invalid("assetId", "This category forbids an asset.");
        if ((category.getAssetPolicy() == AssetPolicy.REQUIRED || form.isRequiresEquipment())
                && asset == null)
            throw TicketSubmissionException.invalid("assetId", "An eligible asset is required.");
        if (asset != null
                && (!asset.getLocation().getId().equals(location.getId())
                        || !(asset.getStatus() == AssetStatus.ACTIVE
                                || asset.getStatus() == AssetStatus.UNDER_MAINTENANCE)))
            throw TicketSubmissionException.invalid(
                    "assetId", "Choose an eligible asset at the exact selected location.");
        Skill skill =
                form.getDefaultSkillId() == null
                        ? null
                        : locked(Skill.class, form.getDefaultSkillId());
        SlaPolicy sla =
                form.getDefaultSlaPolicyId() == null
                        ? null
                        : locked(SlaPolicy.class, form.getDefaultSlaPolicyId());
        if ((skill != null && !skill.isActive()) || (sla != null && !sla.isActive()))
            throw new TicketSubmissionException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    com.nexora.common.exception.ProblemTypes.STALE_WRITE,
                    "A configured skill or SLA is no longer active.",
                    java.util.Map.of(
                            "formVersionId",
                            "Reload the form after its references are corrected."));
        var validated =
                validator.validate(
                        form,
                        fields.findByFormVersionIdOrderByDisplayOrderAscIdAsc(form.getId()),
                        options.findByFieldDefinitionFormVersionIdOrderByDisplayOrderAscIdAsc(
                                form.getId()),
                        request,
                        files);
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        Ticket ticket =
                new Ticket(
                        numbers.nextNumber(createdAt),
                        reporter,
                        category,
                        form,
                        location,
                        asset,
                        request.title(),
                        request.description(),
                        Priority.valueOf(category.getDefaultPriority().name()),
                        sla == null ? null : createdAt.plusMinutes(sla.getResolutionMinutes()),
                        skill,
                        createdAt);
        em.persist(ticket);
        em.flush();
        validated
                .answers()
                .forEach(
                        (field, value) ->
                                em.persist(new TicketFieldValue(ticket, field, value.toString())));
        for (int i = 0; i < files.size(); i++) {
            StoredFile stored = storage.store(files.get(i));
            cleanup.deleteOnRollback(stored.storageKey());
            em.persist(
                    new TicketAttachment(
                            ticket,
                            validated.imageFields().get(i),
                            reporter,
                            AttachmentType.REPORT_EVIDENCE,
                            stored.storageKey(),
                            stored.originalFilename(),
                            stored.contentType(),
                            stored.size(),
                            createdAt));
        }
        em.persist(
                new TicketStatusHistory(
                        ticket,
                        null,
                        TicketStatus.SUBMITTED,
                        TicketAction.SUBMIT,
                        null,
                        reporter,
                        createdAt));
        em.flush();
        return CreateTicketResponse.from(ticket);
    }

    private <T> T locked(Class<T> type, Long id) {
        T value = em.find(type, id, LockModeType.PESSIMISTIC_READ);
        if (value == null)
            throw new ResourceNotFoundException(type.getSimpleName() + " not found.");
        // Also refresh references already present in the persistence context (e.g. form.category or
        // auth user).
        em.refresh(value, LockModeType.PESSIMISTIC_READ);
        return value;
    }
}
