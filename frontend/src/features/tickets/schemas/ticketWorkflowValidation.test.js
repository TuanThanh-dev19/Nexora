import { describe, expect, it } from 'vitest'
import {
  availableTicketActions,
  workflowHistoryAction,
} from '../constants/ticketWorkflow'
import {
  candidateEligibility,
  validateWorkflowPayload,
} from './ticketWorkflowValidation'

const candidate = {
  technicianId: 21,
  userStatus: 'ACTIVE',
  hasProfile: true,
  hasTechnicianRole: true,
  eligibilityReasons: [],
}

describe('manager workflow presentation', () => {
  const matrix = [
    ['SUBMITTED', ['review', 'reject', 'cancel']],
    ['UNDER_REVIEW', ['reject', 'cancel', 'assign']],
    ['REOPENED', ['cancel', 'assign']],
    ['ASSIGNED', ['cancel', 'reassign', 'unassign']],
    ['IN_PROGRESS', ['cancel', 'unassign']],
    ['RESOLVED', ['reopen']],
    ['CLOSED', []],
    ['REJECTED', []],
    ['CANCELLED', []],
  ]
  it.each(matrix)(
    'exposes only the issue actions from %s for Manager/Admin',
    (status, actions) => {
      for (const roles of [['MANAGER'], ['ADMIN'], ['REQUESTER', 'MANAGER']])
        expect(
          availableTicketActions(
            { status, currentAssignment: { technician: { technicianId: 21 } } },
            { roles },
          ),
        ).toEqual(actions)
    },
  )
  it.each([['REQUESTER'], ['TECHNICIAN'], ['REQUESTER', 'TECHNICIAN'], []])(
    'does not expose manager actions to %j',
    (...roles) => {
      for (const [status] of matrix)
        expect(availableTicketActions({ status }, { roles })).toEqual([])
    },
  )
  it('does not grant actions to inactive users or unknown states', () => {
    expect(
      availableTicketActions(
        { status: 'SUBMITTED' },
        {
          roles: ['ADMIN'],
          status: 'DISABLED',
        },
      ),
    ).toEqual([])
    expect(
      availableTicketActions({ status: 'UNKNOWN' }, { roles: ['MANAGER'] }),
    ).toEqual([])
  })
  it('distinguishes the two cancellation history actions', () => {
    expect(workflowHistoryAction('cancel', 'UNDER_REVIEW')).toBe(
      'CANCEL_BEFORE_ASSIGNMENT',
    )
    expect(workflowHistoryAction('cancel', 'REOPENED')).toBe(
      'CANCEL_AFTER_ASSIGNMENT',
    )
  })
})

describe('workflow form validation', () => {
  it('allows 1000 trimmed characters only for reassignment', () => {
    const payload = {
      version: 2,
      technicianId: 21,
      reason: `  ${'a'.repeat(1000)}  `,
    }
    expect(validateWorkflowPayload('reassign', payload, candidate)).toEqual({})
    expect(
      validateWorkflowPayload(
        'reassign',
        { ...payload, reason: 'a'.repeat(1001) },
        candidate,
      ),
    ).toHaveProperty('reason')
    expect(validateWorkflowPayload('unassign', payload)).toHaveProperty(
      'reason',
    )
    expect(
      availableTicketActions(
        { status: 'ASSIGNED', currentAssignment: null },
        { roles: ['MANAGER'] },
      ),
    ).not.toContain('reassign')
  })
  it('accepts version zero and review without a reason', () => {
    expect(validateWorkflowPayload('review', { version: 0 })).toEqual({})
  })
  it.each(['reject', 'cancel', 'reassign', 'unassign'])(
    'requires a nonblank reason for %s',
    (action) => {
      const payload = { version: 0, technicianId: 21, reason: '   ' }
      expect(
        validateWorkflowPayload(action, payload, candidate),
      ).toHaveProperty('reason')
      expect(
        validateWorkflowPayload(
          action,
          { ...payload, reason: 'Đổi người xử lý' },
          candidate,
        ),
      ).toEqual({})
    },
  )
  it('validates versions, selection and agreed reason limits', () => {
    for (const version of [undefined, -1, '0', 0.5])
      expect(validateWorkflowPayload('review', { version })).toHaveProperty(
        'version',
      )
    expect(validateWorkflowPayload('assign', { version: 0 })).toHaveProperty(
      'technicianId',
    )
    expect(
      validateWorkflowPayload('reject', {
        version: 0,
        reason: 'a'.repeat(501),
      }),
    ).toHaveProperty('reason')
    expect(
      validateWorkflowPayload('reject', {
        version: 0,
        reason: 'a'.repeat(501),
      }),
    ).toHaveProperty('reason')
  })
  it.each([
    'UNAVAILABLE',
    'CAPACITY_REACHED',
    'SKILL_MISMATCH',
    'COVERAGE_MISMATCH',
    'USER_INACTIVE',
    'MISSING_TECHNICIAN_ROLE',
    'MISSING_TECHNICIAN_PROFILE',
  ])('blocks candidate and rejects assignment for %s', (code) => {
    const mismatch = {
      ...candidate,
      eligibilityReasons: [{ code, overridable: false }],
    }
    expect(candidateEligibility(mismatch)).toEqual({
      blocked: true,
      requiresOverride: false,
    })
    expect(
      validateWorkflowPayload(
        'assign',
        { version: 1, technicianId: 21 },
        mismatch,
      ),
    ).toHaveProperty('technicianId')
  })
  it.each([
    { userStatus: 'LOCKED' },
    { userStatus: 'DISABLED' },
    { hasProfile: false },
    { hasTechnicianRole: false },
    { eligibilityReasons: [{ code: 'USER_INACTIVE', overridable: false }] },
    { eligibilityReasons: [{ code: 'UNKNOWN_REASON', overridable: false }] },
    { eligibilityReasons: [{ code: 'CAPACITY_REACHED', overridable: false }] },
  ])('never bypasses blockers: %j', (blocker) => {
    const blocked = { ...candidate, ...blocker }
    expect(candidateEligibility(blocked)).toEqual({
      blocked: true,
      requiresOverride: false,
    })
    expect(
      validateWorkflowPayload(
        'assign',
        { version: 1, technicianId: 21 },
        blocked,
      ),
    ).toHaveProperty('technicianId')
  })
})
