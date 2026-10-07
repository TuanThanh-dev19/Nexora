package com.nexora.ticket.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.user.entity.RoleCode;

/** Immutable policy metadata for one explicit ticket action. */
public record TicketTransitionDefinition(
        TicketAction action,
        TicketTransitionSource source,
        Set<TicketStatus> allowedSourceStatuses,
        TicketStatus targetStatus,
        Map<RoleCode, TicketActorScope> actorPermissions,
        boolean nonBlankReasonRequired,
        Set<TicketTransitionRequirement> requirements,
        Set<TicketTransitionEffect> expectedEffects) {

    public TicketTransitionDefinition {
        action = Objects.requireNonNull(action, "action is required");
        source = Objects.requireNonNull(source, "source is required");
        targetStatus = Objects.requireNonNull(targetStatus, "targetStatus is required");
        allowedSourceStatuses = immutableEnumSet(
                allowedSourceStatuses, TicketStatus.class, "allowedSourceStatuses");
        actorPermissions = immutableActorPermissions(actorPermissions);
        requirements = immutableEnumSet(
                requirements, TicketTransitionRequirement.class, "requirements");
        expectedEffects = immutableEnumSet(
                expectedEffects, TicketTransitionEffect.class, "expectedEffects");

        if (source == TicketTransitionSource.NEW_REQUEST && !allowedSourceStatuses.isEmpty()) {
            throw new IllegalArgumentException("A new-request transition cannot declare ticket source statuses");
        }
        if (source == TicketTransitionSource.EXISTING_TICKET && allowedSourceStatuses.isEmpty()) {
            throw new IllegalArgumentException("An existing-ticket transition requires a source status");
        }
        if (actorPermissions.isEmpty()) {
            throw new IllegalArgumentException("A transition requires at least one actor permission");
        }
    }

    /** Returns the canonical roles that may initiate this action. */
    public Set<RoleCode> permittedActorRoles() {
        return actorPermissions.keySet();
    }

    /** Returns the ticket relationship required for the supplied role, if that role is permitted. */
    public Optional<TicketActorScope> actorScopeFor(RoleCode role) {
        return Optional.ofNullable(actorPermissions.get(Objects.requireNonNull(role, "role is required")));
    }

    public boolean requires(TicketTransitionRequirement requirement) {
        return requirements.contains(Objects.requireNonNull(requirement, "requirement is required"));
    }

    private static Map<RoleCode, TicketActorScope> immutableActorPermissions(
            Map<RoleCode, TicketActorScope> actorPermissions) {
        Objects.requireNonNull(actorPermissions, "actorPermissions is required");
        EnumMap<RoleCode, TicketActorScope> copy = new EnumMap<>(RoleCode.class);
        actorPermissions.forEach((role, scope) -> copy.put(
                Objects.requireNonNull(role, "actor role is required"),
                Objects.requireNonNull(scope, "actor scope is required")));
        return Collections.unmodifiableMap(copy);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(
            Set<E> values, Class<E> enumType, String fieldName) {
        Objects.requireNonNull(values, fieldName + " is required");
        EnumSet<E> copy = values.isEmpty()
                ? EnumSet.noneOf(enumType)
                : EnumSet.copyOf(values);
        return Collections.unmodifiableSet(copy);
    }
}
