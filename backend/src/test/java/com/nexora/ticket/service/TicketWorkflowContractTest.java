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
import static com.nexora.user.entity.RoleCode.ADMIN;
import static com.nexora.user.entity.RoleCode.MANAGER;
import static com.nexora.user.entity.RoleCode.REQUESTER;
import static com.nexora.user.entity.RoleCode.TECHNICIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.nexora.common.exception.InvalidStateTransitionException;
import com.nexora.ticket.entity.TicketAction;
import com.nexora.ticket.entity.TicketStatus;
import com.nexora.user.entity.RoleCode;

class TicketWorkflowContractTest {

    @Test
    void exposesOnlyCanonicalStatusesAndActions() {
        assertThat(TicketStatus.values()).containsExactly(
                TicketStatus.SUBMITTED,
                TicketStatus.UNDER_REVIEW,
                TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS,
                TicketStatus.RESOLVED,
                TicketStatus.REOPENED,
                TicketStatus.CLOSED,
                TicketStatus.REJECTED,
                TicketStatus.CANCELLED);
        assertThat(TicketAction.values()).containsExactly(
                TicketAction.SUBMIT,
                TicketAction.BEGIN_REVIEW,
                TicketAction.REJECT,
                TicketAction.CANCEL_BEFORE_ASSIGNMENT,
                TicketAction.ASSIGN,
                TicketAction.START_WORK,
                TicketAction.UNASSIGN,
                TicketAction.REASSIGN,
                TicketAction.CANCEL_AFTER_ASSIGNMENT,
                TicketAction.RESOLVE,
                TicketAction.CLOSE,
                TicketAction.FORCE_CLOSE,
                TicketAction.REOPEN);
    }

    @Test
    void definesSubmitAsTheOnlyCreationTransition() {
        TicketTransitionDefinition submit = TicketWorkflowContract.requireCreation(TicketAction.SUBMIT);

        assertThat(submit.source()).isEqualTo(TicketTransitionSource.NEW_REQUEST);
        assertThat(submit.allowedSourceStatuses()).isEmpty();
        assertThat(submit.targetStatus()).isEqualTo(TicketStatus.SUBMITTED);
        assertThat(submit.actorPermissions()).containsExactlyEntriesOf(
                Map.of(REQUESTER, SELF_SUBMITTER));
    }

    @ParameterizedTest
    @EnumSource(value = TicketAction.class, names = "SUBMIT", mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonSubmitActionsForNewRequests(TicketAction action) {
        assertThatThrownBy(() -> TicketWorkflowContract.requireCreation(action))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(action.name());
    }

    @ParameterizedTest
    @MethodSource("validExistingTransitions")
    void allowsEveryApprovedExistingTicketTransition(
            TicketStatus source, TicketAction action, TicketStatus target) {
        TicketTransitionDefinition definition = TicketWorkflowContract.requireTransition(source, action);

        assertThat(TicketWorkflowContract.isAllowed(source, action)).isTrue();
        assertThat(definition.targetStatus()).isEqualTo(target);
        assertThat(TicketWorkflowContract.allowedActionsFrom(source)).contains(action);
    }

    @ParameterizedTest
    @MethodSource("allowedActionsBySource")
    void exposesTheCompleteActionSetForEachNonTerminalStatus(
            TicketStatus source, Set<TicketAction> expectedActions) {
        assertThat(TicketWorkflowContract.allowedActionsFrom(source))
                .containsExactlyInAnyOrderElementsOf(expectedActions);
    }

    @ParameterizedTest
    @MethodSource("invalidExistingTransitions")
    void rejectsUnsupportedStatusActionCombinations(TicketStatus source, TicketAction action) {
        assertThat(TicketWorkflowContract.isAllowed(source, action)).isFalse();
        assertThatThrownBy(() -> TicketWorkflowContract.requireTransition(source, action))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining(source.name())
                .hasMessageContaining(action.name());
    }

    @ParameterizedTest
    @MethodSource("actorPermissions")
    void definesExactActorRolesAndTicketScopes(
            TicketAction action, Map<RoleCode, TicketActorScope> expectedPermissions) {
        TicketTransitionDefinition definition = TicketWorkflowContract.definitionFor(action);

        assertThat(definition.actorPermissions()).containsExactlyInAnyOrderEntriesOf(expectedPermissions);
        assertThat(definition.permittedActorRoles())
                .containsExactlyInAnyOrderElementsOf(expectedPermissions.keySet());
        expectedPermissions.forEach((role, scope) ->
                assertThat(definition.actorScopeFor(role)).contains(scope));
        Arrays.stream(RoleCode.values())
                .filter(role -> !expectedPermissions.containsKey(role))
                .forEach(role -> assertThat(definition.actorScopeFor(role)).isEmpty());
    }

    @Test
    void marksExactlyTheActionsThatRequireANonBlankReason() {
        Set<TicketAction> reasonRequired = Set.of(
                TicketAction.REJECT,
                TicketAction.CANCEL_BEFORE_ASSIGNMENT,
                TicketAction.UNASSIGN,
                TicketAction.REASSIGN,
                TicketAction.CANCEL_AFTER_ASSIGNMENT,
                TicketAction.FORCE_CLOSE,
                TicketAction.REOPEN);

        for (TicketAction action : TicketAction.values()) {
            assertThat(TicketWorkflowContract.definitionFor(action).nonBlankReasonRequired())
                    .as("reason requirement for %s", action)
                    .isEqualTo(reasonRequired.contains(action));
        }
    }

    @Test
    void definesMandatorySubmissionInputs() {
        assertThat(TicketWorkflowContract.definitionFor(TicketAction.SUBMIT).requirements())
                .containsExactlyInAnyOrder(
                        VALID_CATEGORY_AND_PUBLISHED_FORM,
                        VALID_LOCATION,
                        ASSET_POLICY_SATISFIED,
                        VALID_DYNAMIC_FIELD_VALUES,
                        REQUIRED_EVIDENCE_PRESENT);
    }

    @Test
    void definesAssignmentAndConditionalOverrideInputs() {
        Set<TicketTransitionRequirement> assignmentRequirements = Set.of(
                ASSIGNEE,
                ASSIGNEE_ELIGIBLE_OR_APPROVED_OVERRIDE,
                NON_BLANK_OVERRIDE_REASON_WHEN_OVERRIDDEN);

        assertThat(TicketWorkflowContract.definitionFor(TicketAction.ASSIGN).requirements())
                .containsExactlyInAnyOrderElementsOf(assignmentRequirements);
        assertThat(TicketWorkflowContract.definitionFor(TicketAction.REASSIGN).requirements())
                .containsExactlyInAnyOrderElementsOf(assignmentRequirements);
    }

    @Test
    void definesMandatoryResolutionInputs() {
        assertThat(TicketWorkflowContract.definitionFor(TicketAction.RESOLVE).requirements())
                .containsExactlyInAnyOrder(
                        RESOLUTION_OUTCOME,
                        NON_BLANK_RESOLUTION_SUMMARY,
                        EXISTING_WORK_LOG);
    }

    @Test
    void actionsWithoutAdditionalMandatoryInputsExposeAnEmptyRequirementSet() {
        assertThat(Arrays.stream(TicketAction.values())
                .filter(action -> TicketWorkflowContract.definitionFor(action).requirements().isEmpty()))
                .containsExactly(
                        TicketAction.BEGIN_REVIEW,
                        TicketAction.REJECT,
                        TicketAction.CANCEL_BEFORE_ASSIGNMENT,
                        TicketAction.START_WORK,
                        TicketAction.UNASSIGN,
                        TicketAction.CANCEL_AFTER_ASSIGNMENT,
                        TicketAction.CLOSE,
                        TicketAction.FORCE_CLOSE,
                        TicketAction.REOPEN);
    }

    @ParameterizedTest
    @MethodSource("expectedEffects")
    void documentsExactExpectedEffects(
            TicketAction action, Set<TicketTransitionEffect> expectedEffects) {
        assertThat(TicketWorkflowContract.definitionFor(action).expectedEffects())
                .containsExactlyInAnyOrderElementsOf(expectedEffects);
    }

    @ParameterizedTest
    @MethodSource("terminalStatusActions")
    void rejectsEveryActionFromTerminalStatuses(TicketStatus terminalStatus, TicketAction action) {
        assertThat(TicketWorkflowContract.isTerminal(terminalStatus)).isTrue();
        assertThat(TicketWorkflowContract.allowedActionsFrom(terminalStatus)).isEmpty();
        assertThat(TicketWorkflowContract.isAllowed(terminalStatus, action)).isFalse();
        assertThatThrownBy(() -> TicketWorkflowContract.requireTransition(terminalStatus, action))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void doesNotClassifyActiveOrResolvedStatusesAsTerminal() {
        assertThat(Arrays.stream(TicketStatus.values())
                .filter(status -> !TicketWorkflowContract.isTerminal(status)))
                .containsExactly(
                        TicketStatus.SUBMITTED,
                        TicketStatus.UNDER_REVIEW,
                        TicketStatus.ASSIGNED,
                        TicketStatus.IN_PROGRESS,
                        TicketStatus.RESOLVED,
                        TicketStatus.REOPENED);
    }

    @Test
    void transitionMetadataIsImmutable() {
        TicketTransitionDefinition definition = TicketWorkflowContract.definitionFor(TicketAction.ASSIGN);

        assertThatThrownBy(() -> definition.allowedSourceStatuses().add(TicketStatus.SUBMITTED))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.actorPermissions().put(REQUESTER, TICKET_OWNER))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.permittedActorRoles().add(REQUESTER))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.requirements().add(EXISTING_WORK_LOG))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.expectedEffects().add(SET_CLOSED_AT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Stream<Arguments> validExistingTransitions() {
        return Stream.of(
                transition(TicketStatus.SUBMITTED, TicketAction.BEGIN_REVIEW, TicketStatus.UNDER_REVIEW),
                transition(TicketStatus.SUBMITTED, TicketAction.REJECT, TicketStatus.REJECTED),
                transition(TicketStatus.UNDER_REVIEW, TicketAction.REJECT, TicketStatus.REJECTED),
                transition(TicketStatus.SUBMITTED, TicketAction.CANCEL_BEFORE_ASSIGNMENT, TicketStatus.CANCELLED),
                transition(TicketStatus.UNDER_REVIEW, TicketAction.CANCEL_BEFORE_ASSIGNMENT, TicketStatus.CANCELLED),
                transition(TicketStatus.UNDER_REVIEW, TicketAction.ASSIGN, TicketStatus.ASSIGNED),
                transition(TicketStatus.REOPENED, TicketAction.ASSIGN, TicketStatus.ASSIGNED),
                transition(TicketStatus.ASSIGNED, TicketAction.START_WORK, TicketStatus.IN_PROGRESS),
                transition(TicketStatus.ASSIGNED, TicketAction.UNASSIGN, TicketStatus.UNDER_REVIEW),
                transition(TicketStatus.IN_PROGRESS, TicketAction.UNASSIGN, TicketStatus.UNDER_REVIEW),
                transition(TicketStatus.ASSIGNED, TicketAction.REASSIGN, TicketStatus.ASSIGNED),
                transition(TicketStatus.ASSIGNED, TicketAction.CANCEL_AFTER_ASSIGNMENT, TicketStatus.CANCELLED),
                transition(TicketStatus.IN_PROGRESS, TicketAction.CANCEL_AFTER_ASSIGNMENT, TicketStatus.CANCELLED),
                transition(TicketStatus.REOPENED, TicketAction.CANCEL_AFTER_ASSIGNMENT, TicketStatus.CANCELLED),
                transition(TicketStatus.IN_PROGRESS, TicketAction.RESOLVE, TicketStatus.RESOLVED),
                transition(TicketStatus.RESOLVED, TicketAction.CLOSE, TicketStatus.CLOSED),
                transition(TicketStatus.RESOLVED, TicketAction.FORCE_CLOSE, TicketStatus.CLOSED),
                transition(TicketStatus.RESOLVED, TicketAction.REOPEN, TicketStatus.REOPENED));
    }

    private static Stream<Arguments> allowedActionsBySource() {
        return Stream.of(
                Arguments.of(TicketStatus.SUBMITTED, Set.of(
                        TicketAction.BEGIN_REVIEW,
                        TicketAction.REJECT,
                        TicketAction.CANCEL_BEFORE_ASSIGNMENT)),
                Arguments.of(TicketStatus.UNDER_REVIEW, Set.of(
                        TicketAction.REJECT,
                        TicketAction.CANCEL_BEFORE_ASSIGNMENT,
                        TicketAction.ASSIGN)),
                Arguments.of(TicketStatus.ASSIGNED, Set.of(
                        TicketAction.START_WORK,
                        TicketAction.UNASSIGN,
                        TicketAction.REASSIGN,
                        TicketAction.CANCEL_AFTER_ASSIGNMENT)),
                Arguments.of(TicketStatus.IN_PROGRESS, Set.of(
                        TicketAction.UNASSIGN,
                        TicketAction.CANCEL_AFTER_ASSIGNMENT,
                        TicketAction.RESOLVE)),
                Arguments.of(TicketStatus.RESOLVED, Set.of(
                        TicketAction.CLOSE,
                        TicketAction.FORCE_CLOSE,
                        TicketAction.REOPEN)),
                Arguments.of(TicketStatus.REOPENED, Set.of(
                        TicketAction.ASSIGN,
                        TicketAction.CANCEL_AFTER_ASSIGNMENT)));
    }

    private static Stream<Arguments> invalidExistingTransitions() {
        return Stream.of(
                Arguments.of(TicketStatus.IN_PROGRESS, TicketAction.REASSIGN),
                Arguments.of(TicketStatus.UNDER_REVIEW, TicketAction.BEGIN_REVIEW),
                Arguments.of(TicketStatus.SUBMITTED, TicketAction.ASSIGN),
                Arguments.of(TicketStatus.UNDER_REVIEW, TicketAction.START_WORK),
                Arguments.of(TicketStatus.ASSIGNED, TicketAction.RESOLVE),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketAction.CLOSE),
                Arguments.of(TicketStatus.SUBMITTED, TicketAction.CANCEL_AFTER_ASSIGNMENT),
                Arguments.of(TicketStatus.ASSIGNED, TicketAction.CANCEL_BEFORE_ASSIGNMENT),
                Arguments.of(TicketStatus.REOPENED, TicketAction.START_WORK),
                Arguments.of(TicketStatus.SUBMITTED, TicketAction.SUBMIT));
    }

    private static Stream<Arguments> actorPermissions() {
        Map<RoleCode, TicketActorScope> managers = Map.of(
                MANAGER, NO_TICKET_RELATION_REQUIRED,
                ADMIN, NO_TICKET_RELATION_REQUIRED);
        return Stream.of(
                Arguments.of(TicketAction.SUBMIT, Map.of(REQUESTER, SELF_SUBMITTER)),
                Arguments.of(TicketAction.BEGIN_REVIEW, managers),
                Arguments.of(TicketAction.REJECT, managers),
                Arguments.of(TicketAction.CANCEL_BEFORE_ASSIGNMENT, Map.of(
                        REQUESTER, TICKET_OWNER,
                        MANAGER, NO_TICKET_RELATION_REQUIRED,
                        ADMIN, NO_TICKET_RELATION_REQUIRED)),
                Arguments.of(TicketAction.ASSIGN, managers),
                Arguments.of(TicketAction.START_WORK, Map.of(TECHNICIAN, CURRENT_ACTIVE_ASSIGNEE)),
                Arguments.of(TicketAction.UNASSIGN, managers),
                Arguments.of(TicketAction.REASSIGN, managers),
                Arguments.of(TicketAction.CANCEL_AFTER_ASSIGNMENT, managers),
                Arguments.of(TicketAction.RESOLVE, Map.of(TECHNICIAN, CURRENT_ACTIVE_ASSIGNEE)),
                Arguments.of(TicketAction.CLOSE, Map.of(REQUESTER, TICKET_OWNER)),
                Arguments.of(TicketAction.FORCE_CLOSE, managers),
                Arguments.of(TicketAction.REOPEN, Map.of(
                        REQUESTER, TICKET_OWNER,
                        MANAGER, NO_TICKET_RELATION_REQUIRED,
                        ADMIN, NO_TICKET_RELATION_REQUIRED)));
    }

    private static Stream<Arguments> terminalStatusActions() {
        return Stream.of(TicketStatus.CLOSED, TicketStatus.REJECTED, TicketStatus.CANCELLED)
                .flatMap(status -> Arrays.stream(TicketAction.values())
                        .map(action -> Arguments.of(status, action)));
    }

    private static Stream<Arguments> expectedEffects() {
        return Stream.of(
                Arguments.of(TicketAction.SUBMIT, Set.of(
                        WRITE_STATUS_HISTORY, CALCULATE_SLA_DUE_AT)),
                Arguments.of(TicketAction.BEGIN_REVIEW, Set.of(WRITE_STATUS_HISTORY)),
                Arguments.of(TicketAction.REJECT, Set.of(WRITE_STATUS_HISTORY)),
                Arguments.of(TicketAction.CANCEL_BEFORE_ASSIGNMENT, Set.of(WRITE_STATUS_HISTORY)),
                Arguments.of(TicketAction.ASSIGN, Set.of(
                        WRITE_STATUS_HISTORY, CREATE_ACTIVE_ASSIGNMENT)),
                Arguments.of(TicketAction.START_WORK, Set.of(
                        WRITE_STATUS_HISTORY, SET_ACTIVE_ASSIGNMENT_ACCEPTED_AT)),
                Arguments.of(TicketAction.UNASSIGN, Set.of(
                        WRITE_STATUS_HISTORY, END_ACTIVE_ASSIGNMENT)),
                Arguments.of(TicketAction.REASSIGN, Set.of(
                        WRITE_STATUS_HISTORY, REPLACE_ACTIVE_ASSIGNMENT_ATOMICALLY)),
                Arguments.of(TicketAction.CANCEL_AFTER_ASSIGNMENT, Set.of(
                        WRITE_STATUS_HISTORY, END_ACTIVE_ASSIGNMENT_IF_PRESENT)),
                Arguments.of(TicketAction.RESOLVE, Set.of(
                        WRITE_STATUS_HISTORY, END_ACTIVE_ASSIGNMENT, SET_RESOLVED_AT)),
                Arguments.of(TicketAction.CLOSE, Set.of(
                        WRITE_STATUS_HISTORY, SET_CLOSED_AT)),
                Arguments.of(TicketAction.FORCE_CLOSE, Set.of(
                        WRITE_STATUS_HISTORY, SET_CLOSED_AT)),
                Arguments.of(TicketAction.REOPEN, Set.of(
                        WRITE_STATUS_HISTORY, CLEAR_RESOLVED_AT, CALCULATE_SLA_DUE_AT)));
    }

    private static Arguments transition(
            TicketStatus source, TicketAction action, TicketStatus target) {
        return Arguments.of(source, action, target);
    }
}
