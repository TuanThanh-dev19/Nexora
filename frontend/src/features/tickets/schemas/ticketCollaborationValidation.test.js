import { describe, expect, it } from 'vitest'
import {
  availableTicketActions,
  workflowRequiresReason,
  workflowHistoryAction,
} from '../constants/ticketWorkflow'
import {
  canCreateComment,
  canEditComment,
  canReadInternalComments,
  canSubmitFeedback,
} from '../constants/ticketCollaboration'
import {
  validateComment,
  validateFeedback,
} from './ticketCollaborationValidation'

const owner = { id: 4, status: 'ACTIVE', roles: ['REQUESTER'] }
const manager = { id: 5, status: 'ACTIVE', roles: ['MANAGER'] }
const technician = { id: 21, status: 'ACTIVE', roles: ['TECHNICIAN'] }
const ticket = {
  reporter: { id: 4 },
  status: 'SUBMITTED',
  currentAssignment: null,
}

describe('collaboration policy', () => {
  it.each([
    ['SUBMITTED', ['cancel']],
    ['UNDER_REVIEW', ['cancel']],
    ['ASSIGNED', []],
    ['IN_PROGRESS', []],
    ['REOPENED', []],
    ['RESOLVED', ['close', 'reopen']],
    ['CLOSED', []],
    ['REJECTED', []],
    ['CANCELLED', []],
  ])(
    'restricts Requester actions from %s by ownership and status',
    (status, actions) => {
      expect(availableTicketActions({ ...ticket, status }, owner)).toEqual(
        actions,
      )
      expect(
        availableTicketActions({ ...ticket, status }, { ...owner, id: 99 }),
      ).toEqual([])
      expect(
        availableTicketActions(
          { ...ticket, status },
          { ...owner, status: 'LOCKED' },
        ),
      ).toEqual([])
    },
  )
  it('uses reporter close for mixed roles and denies close to other managers', () => {
    const mixed = { ...owner, roles: ['ADMIN', 'REQUESTER'] }
    const resolved = { ...ticket, status: 'RESOLVED' }
    expect(availableTicketActions(resolved, mixed)).toEqual(['close', 'reopen'])
    expect(workflowRequiresReason('close', resolved, mixed)).toBe(false)
    expect(workflowHistoryAction('close', 'RESOLVED', resolved, mixed)).toBe(
      'CLOSE',
    )
    expect(availableTicketActions(resolved, manager)).not.toContain('close')
  })
  it('separates historical visibility, current-assignment commenting and own editing', () => {
    const assigned = {
      ...ticket,
      status: 'IN_PROGRESS',
      currentAssignment: { endedAt: null, technician: { technicianId: 21 } },
    }
    expect(canCreateComment(ticket, owner, 'PUBLIC')).toBe(true)
    expect(canCreateComment(ticket, owner, 'INTERNAL')).toBe(false)
    expect(canReadInternalComments(owner)).toBe(false)
    expect(canCreateComment(assigned, technician, 'INTERNAL')).toBe(true)
    expect(
      canCreateComment({ ...assigned, currentAssignment: null }, technician),
    ).toBe(false)
    const own = { author: { id: 21 }, visibility: 'INTERNAL' }
    expect(canEditComment(ticket, technician, own)).toBe(true)
    expect(canEditComment(ticket, manager, own)).toBe(false)
    expect(
      canEditComment({ ...ticket, status: 'CLOSED' }, technician, own),
    ).toBe(false)
    expect(canCreateComment({ ...ticket, status: 'CANCELLED' }, manager)).toBe(
      false,
    )
    expect(canCreateComment(ticket, { ...manager, status: 'DISABLED' })).toBe(
      false,
    )
  })
  it('permits feedback only for a closed ticket reporter with Requester capability', () => {
    expect(canSubmitFeedback({ ...ticket, status: 'CLOSED' }, owner)).toBe(true)
    expect(canSubmitFeedback(ticket, owner)).toBe(false)
    expect(canSubmitFeedback({ ...ticket, status: 'CLOSED' }, manager)).toBe(
      false,
    )
    expect(
      canSubmitFeedback({ ...ticket, status: 'CLOSED' }, { ...owner, id: 99 }),
    ).toBe(false)
  })
})

describe('collaboration input validation', () => {
  it('validates comment content and PUBLIC/INTERNAL, with content-only edits', () => {
    expect(validateComment({ content: 'Hello', visibility: 'PUBLIC' })).toEqual(
      {},
    )
    expect(validateComment({ content: 'Changed' }, true)).toEqual({})
    for (const content of ['', '   ', 'a'.repeat(2001), null])
      expect(validateComment({ content, visibility: 'PUBLIC' })).toHaveProperty(
        'content',
      )
    expect(
      validateComment({ content: 'Hi', visibility: 'SECRET' }),
    ).toHaveProperty('visibility')
  })
  it('requires integer rating 1–5 and limits optional feedback comment', () => {
    for (const rating of [1, 2, 3, 4, 5])
      expect(validateFeedback({ rating })).toEqual({})
    for (const rating of [null, 0, 6, 2.5, '5'])
      expect(validateFeedback({ rating })).toHaveProperty('rating')
    expect(
      validateFeedback({ rating: 5, comment: 'a'.repeat(1001) }),
    ).toHaveProperty('comment')
  })
})
