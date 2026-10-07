package com.nexora.ticket.service;

import static com.nexora.ticket.service.TicketActorScope.CURRENT_ACTIVE_ASSIGNEE;
import static com.nexora.ticket.service.TicketActorScope.NO_TICKET_RELATION_REQUIRED;
import static com.nexora.ticket.service.TicketActorScope.SELF_SUBMITTER;
import static com.nexora.ticket.service.TicketActorScope.TICKET_OWNER;
import static com.nexora.ticket.service.TicketTransitionEffect.CALCULATE_SLA_DUE_AT;
import static com.nexora.ticket.service.TicketTransitionEffect.CLEAR_RESOLVED_AT;
import static com.nexora.ticket.service.TicketTransitionEffect.CREATE_ACTIVE_ASSIGNMENT;
import static com.nexora.ticket.service.TicketTransitionEffect.END_ACTIVE_ASSIGNMENT;
import static com.nexora.ticket.service.TicketTransitionEffect.END_ACTIVE_ASSIGNMENT_IF_PRESENT;
import static com.nexora.ticket.service.TicketTransitionEffect.REPLACE_ACTIVE_ASSIGNMENT_ATOMICALLY;
import static com.nexora.ticket.service.TicketTransitionEffect.SET_ACTIVE_ASSIGNMENT_ACCEPTED_AT;
import static com.nexora.ticket.service.TicketTransitionEffect.SET_CLOSED_AT;
import static com.nexora.ticket.service.TicketTransitionEffect.SET_RESOLVED_AT;
import static com.nexora.ticket.service.TicketTransitionEffect.WRITE_STATUS_HISTORY;
import static com.nexora.ticket.service.TicketTransitionRequirement.ASSET_POLICY_SATISFIED;
import static com.nexora.ticket.service.TicketTransitionRequirement.ASSIGNEE;
import static com.nexora.ticket.service.TicketTransitionRequirement.ASSIGNEE_ELIGIBLE_OR_APPROVED_OVERRIDE;
import static com.nexora.ticket.service.TicketTransitionRequirement.EXISTING_WORK_LOG;
import static com.nexora.ticket.service.TicketTransitionRequirement.NON_BLANK_OVERRIDE_REASON_WHEN_OVERRIDDEN;
import static com.nexora.ticket.service.TicketTransitionRequirement.NON_BLANK_RESOLUTION_SUMMARY;
import static com.nexora.ticket.service.TicketTransitionRequirement.REQUIRED_EVIDENCE_PRESENT;
import static com.nexora.ticket.service.TicketTransitionRequirement.RESOLUTION_OUTCOME;
import static com.nexora.ticket.service.TicketTransitionRequirement.VALID_CATEGORY_AND_PUBLISHED_FORM;
import static com.nexora.ticket.service.TicketTransitionRequirement.VALID_DYNAMIC_FIELD_VALUES;
import static com.nexora.ticket.service.TicketTransitionRequirement.VALID_LOCATION;
import static com.nexora.ticket.service.TicketTransitionSource.EXISTING_TICKET;
import static com.nexora.ticket.service.TicketTransitionSource.NEW_REQUEST;
import static com.nexora.user.entity.RoleCode.ADMIN;
import static com.nexora.user.entity.RoleCode.MANAGER;
import static com.nexora.user.entity.RoleCode.REQUESTER;
import static com.nexora.user.entity.RoleCode.TECHNICIAN;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.nexora.common.exception.InvalidStateTransitionException;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.user.entity.RoleCode;

/**
 * Canonical, action-driven ticket lifecycle policy.
 *
 * <p>The definitions describe validation and side-effect expectations only. They do not mutate a
 * ticket, assignment, history, timestamp, or SLA value.</p>
 */
public final class TicketWorkflowContract {

    private static final Set<TicketStatus> TERMINAL_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(TicketStatus.CLOSED, TicketStatus.REJECTED, TicketStatus.CANCELLED));

    private static final Map<TicketAction, TicketTransitionDefinition> TRANSITIONS = buildTransitions();

    private TicketWorkflowContract() {
    }

    public static TicketTransitionDefinition definitionFor(TicketAction action) {
        TicketAction requiredAction = Objects.requireNonNull(action, "action is required");
        TicketTransitionDefinition definition = TRANSITIONS.get(requiredAction);
        if (definition == null) {
            throw new IllegalArgumentException("No ticket transition is defined for action " + requiredAction);
        }
        return definition;
    }

    public static TicketTransitionDefinition requireCreation(TicketAction action) {
        TicketTransitionDefinition definition = definitionFor(action);
        if (definition.source() != NEW_REQUEST) {
            throw new InvalidStateTransitionException(
                    "Action " + action + " is not allowed for a new ticket request");
        }
        return definition;
    }

    public static TicketTransitionDefinition requireTransition(TicketStatus sourceStatus, TicketAction action) {
        TicketStatus requiredSource = Objects.requireNonNull(sourceStatus, "sourceStatus is required");
        TicketTransitionDefinition definition = definitionFor(action);
        if (definition.source() != EXISTING_TICKET
                || !definition.allowedSourceStatuses().contains(requiredSource)) {
            throw new InvalidStateTransitionException(
                    "Action " + action + " is not allowed from ticket status " + requiredSource);
        }
        return definition;
    }

    public static boolean isAllowed(TicketStatus sourceStatus, TicketAction action) {
        TicketStatus requiredSource = Objects.requireNonNull(sourceStatus, "sourceStatus is required");
        TicketTransitionDefinition definition = definitionFor(action);
        return definition.source() == EXISTING_TICKET
                && definition.allowedSourceStatuses().contains(requiredSource);
    }

    public static Set<TicketAction> allowedActionsFrom(TicketStatus sourceStatus) {
        TicketStatus requiredSource = Objects.requireNonNull(sourceStatus, "sourceStatus is required");
        EnumSet<TicketAction> actions = EnumSet.noneOf(TicketAction.class);
        TRANSITIONS.forEach((action, definition) -> {
            if (definition.source() == EXISTING_TICKET
                    && definition.allowedSourceStatuses().contains(requiredSource)) {
                actions.add(action);
            }
        });
        return Collections.unmodifiableSet(actions);
    }

    public static boolean isTerminal(TicketStatus status) {
        return TERMINAL_STATUSES.contains(Objects.requireNonNull(status, "status is required"));
    }

    private static Map<TicketAction, TicketTransitionDefinition> buildTransitions() {
        EnumMap<TicketAction, TicketTransitionDefinition> transitions = new EnumMap<>(TicketAction.class);

        put(transitions, definition(
                TicketAction.SUBMIT,
                NEW_REQUEST,
                statuses(),
                TicketStatus.SUBMITTED,
                Map.of(REQUESTER, SELF_SUBMITTER),
                false,
                requirements(
                        VALID_CATEGORY_AND_PUBLISHED_FORM,
                        VALID_LOCATION,
                        ASSET_POLICY_SATISFIED,
                        VALID_DYNAMIC_FIELD_VALUES,
                        REQUIRED_EVIDENCE_PRESENT),
                effects(WRITE_STATUS_HISTORY, CALCULATE_SLA_DUE_AT)));

        put(transitions, definition(
                TicketAction.BEGIN_REVIEW,
                EXISTING_TICKET,
                statuses(TicketStatus.SUBMITTED),
                TicketStatus.UNDER_REVIEW,
                managerAndAdmin(),
                false,
                requirements(),
                effects(WRITE_STATUS_HISTORY)));

        put(transitions, definition(
                TicketAction.REJECT,
                EXISTING_TICKET,
                statuses(TicketStatus.SUBMITTED, TicketStatus.UNDER_REVIEW),
                TicketStatus.REJECTED,
                managerAndAdmin(),
                true,
                requirements(),
                effects(WRITE_STATUS_HISTORY)));

        put(transitions, definition(
                TicketAction.CANCEL_BEFORE_ASSIGNMENT,
                EXISTING_TICKET,
                statuses(TicketStatus.SUBMITTED, TicketStatus.UNDER_REVIEW),
                TicketStatus.CANCELLED,
                Map.of(
                        REQUESTER, TICKET_OWNER,
                        MANAGER, NO_TICKET_RELATION_REQUIRED,
                        ADMIN, NO_TICKET_RELATION_REQUIRED),
                true,
                requirements(),
                effects(WRITE_STATUS_HISTORY)));

        put(transitions, definition(
                TicketAction.ASSIGN,
                EXISTING_TICKET,
                statuses(TicketStatus.UNDER_REVIEW, TicketStatus.REOPENED),
                TicketStatus.ASSIGNED,
                managerAndAdmin(),
                false,
                assignmentRequirements(),
                effects(WRITE_STATUS_HISTORY, CREATE_ACTIVE_ASSIGNMENT)));

        put(transitions, definition(
                TicketAction.START_WORK,
                EXISTING_TICKET,
                statuses(TicketStatus.ASSIGNED),
                TicketStatus.IN_PROGRESS,
                Map.of(TECHNICIAN, CURRENT_ACTIVE_ASSIGNEE),
                false,
                requirements(),
                effects(WRITE_STATUS_HISTORY, SET_ACTIVE_ASSIGNMENT_ACCEPTED_AT)));

        put(transitions, definition(
                TicketAction.UNASSIGN,
                EXISTING_TICKET,
                statuses(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS),
                TicketStatus.UNDER_REVIEW,
                managerAndAdmin(),
                true,
                requirements(),
                effects(WRITE_STATUS_HISTORY, END_ACTIVE_ASSIGNMENT)));

        put(transitions, definition(
                TicketAction.REASSIGN,
                EXISTING_TICKET,
                statuses(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS),
                TicketStatus.ASSIGNED,
                managerAndAdmin(),
                true,
                assignmentRequirements(),
                effects(WRITE_STATUS_HISTORY, REPLACE_ACTIVE_ASSIGNMENT_ATOMICALLY)));

        put(transitions, definition(
                TicketAction.CANCEL_AFTER_ASSIGNMENT,
                EXISTING_TICKET,
                statuses(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.REOPENED),
                TicketStatus.CANCELLED,
                managerAndAdmin(),
                true,
                requirements(),
                effects(WRITE_STATUS_HISTORY, END_ACTIVE_ASSIGNMENT_IF_PRESENT)));

        put(transitions, definition(
                TicketAction.RESOLVE,
                EXISTING_TICKET,
                statuses(TicketStatus.IN_PROGRESS),
                TicketStatus.RESOLVED,
                Map.of(TECHNICIAN, CURRENT_ACTIVE_ASSIGNEE),
                false,
                requirements(RESOLUTION_OUTCOME, NON_BLANK_RESOLUTION_SUMMARY, EXISTING_WORK_LOG),
                effects(WRITE_STATUS_HISTORY, END_ACTIVE_ASSIGNMENT, SET_RESOLVED_AT)));

        put(transitions, definition(
                TicketAction.CLOSE,
                EXISTING_TICKET,
                statuses(TicketStatus.RESOLVED),
                TicketStatus.CLOSED,
                Map.of(REQUESTER, TICKET_OWNER),
                false,
                requirements(),
                effects(WRITE_STATUS_HISTORY, SET_CLOSED_AT)));

        put(transitions, definition(
                TicketAction.FORCE_CLOSE,
                EXISTING_TICKET,
                statuses(TicketStatus.RESOLVED),
                TicketStatus.CLOSED,
                managerAndAdmin(),
                true,
                requirements(),
                effects(WRITE_STATUS_HISTORY, SET_CLOSED_AT)));

        put(transitions, definition(
                TicketAction.REOPEN,
                EXISTING_TICKET,
                statuses(TicketStatus.RESOLVED),
                TicketStatus.REOPENED,
                Map.of(
                        REQUESTER, TICKET_OWNER,
                        MANAGER, NO_TICKET_RELATION_REQUIRED,
                        ADMIN, NO_TICKET_RELATION_REQUIRED),
                true,
                requirements(),
                effects(WRITE_STATUS_HISTORY, CLEAR_RESOLVED_AT, CALCULATE_SLA_DUE_AT)));

        verifyCompleteness(transitions);
        return Collections.unmodifiableMap(transitions);
    }

    private static TicketTransitionDefinition definition(
            TicketAction action,
            TicketTransitionSource source,
            Set<TicketStatus> allowedSourceStatuses,
            TicketStatus targetStatus,
            Map<RoleCode, TicketActorScope> actorPermissions,
            boolean nonBlankReasonRequired,
            Set<TicketTransitionRequirement> requirements,
            Set<TicketTransitionEffect> expectedEffects) {
        return new TicketTransitionDefinition(
                action,
                source,
                allowedSourceStatuses,
                targetStatus,
                actorPermissions,
                nonBlankReasonRequired,
                requirements,
                expectedEffects);
    }

    private static void put(
            EnumMap<TicketAction, TicketTransitionDefinition> transitions,
            TicketTransitionDefinition definition) {
        if (transitions.put(definition.action(), definition) != null) {
            throw new IllegalStateException("Duplicate ticket transition for action " + definition.action());
        }
    }

    private static void verifyCompleteness(Map<TicketAction, TicketTransitionDefinition> transitions) {
        EnumSet<TicketAction> missingActions = EnumSet.allOf(TicketAction.class);
        missingActions.removeAll(transitions.keySet());
        if (!missingActions.isEmpty()) {
            throw new IllegalStateException("Missing ticket transitions for actions " + missingActions);
        }

        long creationTransitionCount = transitions.values().stream()
                .filter(definition -> definition.source() == NEW_REQUEST)
                .count();
        if (creationTransitionCount != 1
                || transitions.get(TicketAction.SUBMIT).source() != NEW_REQUEST) {
            throw new IllegalStateException("SUBMIT must be the only new-request transition");
        }

        transitions.values().forEach(definition -> {
            EnumSet<TicketStatus> terminalSources = EnumSet.copyOf(TERMINAL_STATUSES);
            terminalSources.retainAll(definition.allowedSourceStatuses());
            if (!terminalSources.isEmpty()) {
                throw new IllegalStateException(
                        "Terminal ticket statuses cannot have outgoing actions: " + terminalSources);
            }
        });
    }

    private static Map<RoleCode, TicketActorScope> managerAndAdmin() {
        return Map.of(
                MANAGER, NO_TICKET_RELATION_REQUIRED,
                ADMIN, NO_TICKET_RELATION_REQUIRED);
    }

    private static Set<TicketTransitionRequirement> assignmentRequirements() {
        return requirements(
                ASSIGNEE,
                ASSIGNEE_ELIGIBLE_OR_APPROVED_OVERRIDE,
                NON_BLANK_OVERRIDE_REASON_WHEN_OVERRIDDEN);
    }

    private static Set<TicketStatus> statuses(TicketStatus... statuses) {
        EnumSet<TicketStatus> values = EnumSet.noneOf(TicketStatus.class);
        Collections.addAll(values, statuses);
        return Collections.unmodifiableSet(values);
    }

    private static Set<TicketTransitionRequirement> requirements(
            TicketTransitionRequirement... requirements) {
        EnumSet<TicketTransitionRequirement> values = EnumSet.noneOf(TicketTransitionRequirement.class);
        Collections.addAll(values, requirements);
        return Collections.unmodifiableSet(values);
    }

    private static Set<TicketTransitionEffect> effects(TicketTransitionEffect... effects) {
        EnumSet<TicketTransitionEffect> values = EnumSet.noneOf(TicketTransitionEffect.class);
        Collections.addAll(values, effects);
        return Collections.unmodifiableSet(values);
    }
}
