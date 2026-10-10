import { delay, http, HttpResponse } from 'msw'
import { PRIORITIES } from '../../../shared/constants/priorities'
import { TICKET_STATUSES } from '../constants/ticketStatuses'
import {
  canManageTickets,
  TICKET_WORKFLOW_ACTIONS,
  workflowHistoryAction,
} from '../constants/ticketWorkflow'
import {
  candidateEligibility,
  validateWorkflowPayload,
} from '../schemas/ticketWorkflowValidation'
import {
  createWorkflowFixtures,
  WORKFLOW_MOCK_MANAGER,
} from './ticketWorkflowFixtures'

function problem(status, type, detail, fieldErrors) {
  return HttpResponse.json(
    {
      type: `https://nexora.app/problems/${type}`,
      title: 'Ticket workflow mock',
      status,
      detail,
      ...(fieldErrors && { fieldErrors }),
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function pageResponse(content, page, size) {
  return HttpResponse.json({
    content: content.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: content.length,
    totalPages: Math.ceil(content.length / size),
  })
}

function queryParameters(request, allowed) {
  const params = new URL(request.url).searchParams
  for (const key of params.keys()) {
    if (
      !allowed.includes(key) ||
      params.getAll(key).length !== 1 ||
      !params.get(key).trim()
    )
      return {
        error: problem(
          400,
          'validation-failed',
          'Tham số truy vấn không hợp lệ.',
        ),
      }
  }
  const page = Number(params.get('page') ?? 0)
  const size = Number(params.get('size') ?? 20)
  if (
    !/^\d+$/.test(params.get('page') ?? '0') ||
    !/^\d+$/.test(params.get('size') ?? '20') ||
    !Number.isSafeInteger(page) ||
    size < 1 ||
    size > 100 ||
    page * size > 2147483647
  )
    return {
      error: problem(400, 'invalid-pagination', 'Phân trang không hợp lệ.'),
    }
  return { params, page, size }
}

// This in-memory store simulates the documented contract, not backend security or locking.
export function createTicketWorkflowMock({
  baseUrl = '*/api/v1',
  getActor = () => WORKFLOW_MOCK_MANAGER,
  actionDelay = 0,
} = {}) {
  let store = createWorkflowFixtures()
  let scenario = null
  const visible = (ticket, actor) =>
    canManageTickets(actor) ||
    (actor.roles.includes('REQUESTER') && ticket.reporter.id === actor.id) ||
    (actor.roles.includes('TECHNICIAN') &&
      store.assignments.some(
        (assignment) =>
          assignment.ticketId === ticket.id &&
          assignment.technician.technicianId === actor.id,
      ))
  const candidates = () =>
    store.candidates.map(({ skillMatches, coverageMatches, ...candidate }) => {
      const activeTicketCount = store.assignments.filter(
        (assignment) =>
          assignment.technician.technicianId === candidate.technicianId &&
          assignment.endedAt == null,
      ).length
      const eligibilityReasons = []
      const add = (code, overridable) =>
        eligibilityReasons.push({ code, overridable })
      if (candidate.userStatus !== 'ACTIVE') add('USER_INACTIVE', false)
      if (!candidate.hasTechnicianRole) add('MISSING_TECHNICIAN_ROLE', false)
      if (!candidate.hasProfile) add('MISSING_TECHNICIAN_PROFILE', false)
      if (!candidate.available) add('UNAVAILABLE', true)
      if (
        candidate.hasProfile &&
        activeTicketCount >= candidate.maxActiveTickets
      )
        add('CAPACITY_REACHED', true)
      if (!skillMatches) add('SKILL_MISMATCH', true)
      if (!coverageMatches) add('COVERAGE_MISMATCH', true)
      return { ...candidate, activeTicketCount, eligibilityReasons }
    })
  const authenticate = async (request) => {
    const actor = await getActor(request)
    if (!actor || actor.status !== 'ACTIVE')
      return {
        error: problem(401, 'unauthenticated', 'Phiên đăng nhập không hợp lệ.'),
      }
    if (
      !actor.roles.some((role) =>
        ['REQUESTER', 'TECHNICIAN', 'MANAGER', 'ADMIN'].includes(role),
      )
    )
      return {
        error: problem(403, 'access-denied', 'Không có quyền xem phiếu.'),
      }
    return { actor }
  }
  const ticketEndpoint = `${baseUrl.replace(/\/$/, '')}/tickets`
  const handlers = [
    http.get(ticketEndpoint, async ({ request }) => {
      const auth = await authenticate(request)
      if (auth.error) return auth.error
      const query = queryParameters(request, [
        'page',
        'size',
        'sort',
        'status',
        'priority',
        'locationId',
      ])
      if (query.error) return query.error
      const { params, page, size } = query
      const status = params.get('status')
      const priority = params.get('priority')
      const locationId = params.get('locationId')
      const [sort, direction = 'asc', extra] = (
        params.get('sort') ?? 'createdAt,desc'
      ).split(',')
      if (
        !['createdAt', 'updatedAt', 'ticketNumber'].includes(sort) ||
        !['asc', 'desc'].includes(direction.toLowerCase()) ||
        extra !== undefined
      )
        return problem(400, 'unsupported-sort', 'Sắp xếp không hợp lệ.')
      if (
        (status && !TICKET_STATUSES.includes(status)) ||
        (priority && !PRIORITIES.includes(priority)) ||
        (locationId && !/^[1-9]\d*$/.test(locationId))
      )
        return problem(400, 'unsupported-filter', 'Bộ lọc không hợp lệ.')
      const content = store.tickets
        .filter(
          (ticket) =>
            visible(ticket, auth.actor) &&
            (!status || ticket.status === status) &&
            (!priority || ticket.priority === priority) &&
            (!locationId || String(ticket.location.id) === locationId),
        )
        .sort((a, b) => {
          const comparison = String(a[sort]).localeCompare(String(b[sort]))
          return (
            (direction.toLowerCase() === 'desc' ? -comparison : comparison) ||
            a.id - b.id
          )
        })
        .map(
          ({
            description: _description,
            version: _version,
            formVersion: _formVersion,
            dynamicValues: _dynamicValues,
            attachments: _attachments,
            statusHistory: _statusHistory,
            currentAssignment: _currentAssignment,
            ...summary
          }) => summary,
        )
      return pageResponse(content, page, size)
    }),
    http.get(`${ticketEndpoint}/:ticketId`, async ({ request, params }) => {
      const auth = await authenticate(request)
      if (auth.error) return auth.error
      const ticket = store.tickets.find(
        (item) => String(item.id) === params.ticketId,
      )
      if (!ticket || !visible(ticket, auth.actor))
        return problem(404, 'resource-not-found', 'Không thể truy cập phiếu.')
      return HttpResponse.json(ticket)
    }),
    http.get(
      `${ticketEndpoint}/:ticketId/eligible-technicians`,
      async ({ request, params }) => {
        const auth = await authenticate(request)
        if (auth.error) return auth.error
        if (!canManageTickets(auth.actor))
          return problem(403, 'access-denied', 'Không có quyền phân công.')
        if (
          !store.tickets.some((ticket) => String(ticket.id) === params.ticketId)
        )
          return problem(404, 'resource-not-found', 'Không thể truy cập phiếu.')
        const query = queryParameters(request, [
          'page',
          'size',
          'query',
          'eligibility',
        ])
        if (query.error) return query.error
        const filter = query.params.get('eligibility')
        if (
          filter &&
          !['ELIGIBLE', 'OVERRIDE_REQUIRED', 'BLOCKED'].includes(filter)
        )
          return problem(
            400,
            'unsupported-filter',
            'Bộ lọc ứng viên không hợp lệ.',
          )
        const search = (query.params.get('query') ?? '')
          .trim()
          .toLocaleLowerCase()
        const content = candidates().filter((candidate) => {
          const state = candidateEligibility(candidate)
          return (
            `${candidate.fullName} ${candidate.email}`
              .toLocaleLowerCase()
              .includes(search) &&
            (!filter ||
              (filter === 'BLOCKED'
                ? state.blocked
                : filter === 'OVERRIDE_REQUIRED'
                  ? state.requiresOverride
                  : !state.blocked && !state.requiresOverride))
          )
        })
        return pageResponse(content, query.page, query.size)
      },
    ),
    ...Object.keys(TICKET_WORKFLOW_ACTIONS).map((action) =>
      http.post(
        `${ticketEndpoint}/:ticketId/${action}`,
        async ({ request, params }) => {
          if (actionDelay) await delay(actionDelay)
          const auth = await authenticate(request)
          if (auth.error) return auth.error
          if (!canManageTickets(auth.actor))
            return problem(403, 'access-denied', 'Không có quyền xử lý phiếu.')
          const ticket = store.tickets.find(
            (item) => String(item.id) === params.ticketId,
          )
          if (!ticket)
            return problem(
              404,
              'resource-not-found',
              'Không thể truy cập phiếu.',
            )
          let payload
          try {
            payload = await request.json()
          } catch {
            return problem(400, 'malformed-request', 'JSON không hợp lệ.')
          }
          if (!payload || typeof payload !== 'object' || Array.isArray(payload))
            return problem(
              400,
              'malformed-request',
              'Yêu cầu phải là JSON object.',
            )
          const definition = TICKET_WORKFLOW_ACTIONS[action]
          const assignmentAction = ['assign', 'reassign'].includes(action)
          const fields = [
            'version',
            ...(definition.requiresReason ? ['reason'] : []),
            ...(assignmentAction ? ['technicianId', 'overrideReason'] : []),
          ]
          if (Object.keys(payload).some((key) => !fields.includes(key)))
            return problem(
              400,
              'validation-failed',
              'Yêu cầu chứa trường không được hỗ trợ.',
            )
          const candidate = assignmentAction
            ? candidates().find(
                (item) =>
                  String(item.technicianId) === String(payload.technicianId),
              )
            : undefined
          const errors = validateWorkflowPayload(action, payload, candidate)
          const missingOverride =
            candidateEligibility(candidate).requiresOverride &&
            (typeof payload.overrideReason !== 'string' ||
              !payload.overrideReason.trim())
          if (missingOverride) delete errors.overrideReason
          if (candidate && candidateEligibility(candidate).blocked)
            delete errors.technicianId
          if (Object.keys(errors).length)
            return problem(
              400,
              'validation-failed',
              'Dữ liệu không hợp lệ.',
              errors,
            )
          if (payload.version !== ticket.version)
            return problem(
              409,
              'stale-write',
              'Phiếu đã thay đổi. Vui lòng tải lại.',
            )
          if (scenario) {
            const failure = scenario
            scenario = null
            if (failure === 409) {
              // Simulate a complete competing transition so reload sees consistent data.
              if (ticket.status === 'SUBMITTED')
                applyAction(ticket, 'review', {}, auth.actor)
              else if (['UNDER_REVIEW', 'REOPENED'].includes(ticket.status))
                applyAction(
                  ticket,
                  'assign',
                  { overrideReason: 'Phân công đồng thời trong demo' },
                  auth.actor,
                  candidates().find(
                    (candidate) => candidate.technicianId === 21,
                  ),
                )
              else if (['ASSIGNED', 'IN_PROGRESS'].includes(ticket.status))
                applyAction(
                  ticket,
                  'unassign',
                  { reason: 'Gỡ phân công đồng thời trong demo' },
                  auth.actor,
                )
              return problem(
                409,
                'stale-write',
                'Có thay đổi đồng thời. Vui lòng tải lại.',
              )
            }
            return problem(
              failure,
              failure === 400
                ? 'validation-failed'
                : failure === 403
                  ? 'access-denied'
                  : 'resource-not-found',
              'Lỗi mô phỏng theo kịch bản.',
            )
          }
          if (!definition.statuses.includes(ticket.status))
            return problem(
              422,
              'business-rule-violation',
              'Thao tác không phù hợp trạng thái phiếu.',
            )
          if (assignmentAction && candidateEligibility(candidate).blocked)
            return problem(
              422,
              'business-rule-violation',
              'Kỹ thuật viên không đủ điều kiện và không thể ghi đè.',
            )
          if (missingOverride)
            return problem(
              422,
              'business-rule-violation',
              'Điều kiện ứng viên yêu cầu lý do ghi đè.',
              {
                overrideReason: 'Vui lòng nhập lý do ghi đè điều kiện.',
              },
            )
          if (
            ['reassign', 'unassign'].includes(action) &&
            !ticket.currentAssignment
          )
            return problem(
              422,
              'business-rule-violation',
              'Phiếu không có phân công đang hoạt động.',
            )
          if (action === 'assign' && ticket.currentAssignment)
            return problem(
              422,
              'business-rule-violation',
              'Phiếu đã được phân công.',
            )
          applyAction(ticket, action, payload, auth.actor, candidate)
          return HttpResponse.json({
            id: ticket.id,
            status: ticket.status,
            version: ticket.version,
            currentAssignment: ticket.currentAssignment,
          })
        },
      ),
    ),
  ]

  function applyAction(ticket, action, payload, actor, candidate) {
    const changedAt = new Date(
      Math.max(Date.now(), Date.parse(ticket.updatedAt) + 1),
    ).toISOString()
    const fromStatus = ticket.status
    if (
      ['reassign', 'unassign', 'cancel'].includes(action) &&
      ticket.currentAssignment
    ) {
      ticket.currentAssignment.endedAt = changedAt
      ticket.currentAssignment.endReason = payload.reason.trim()
      ticket.currentAssignment = null
    }
    if (['assign', 'reassign'].includes(action)) {
      const assignment = {
        id: Math.max(0, ...store.assignments.map((item) => item.id)) + 1,
        ticketId: ticket.id,
        technician: {
          technicianId: candidate.technicianId,
          fullName: candidate.fullName,
        },
        assignedBy: { id: actor.id, fullName: actor.fullName },
        assignedAt: changedAt,
        acceptedAt: null,
        endedAt: null,
        endReason: null,
        overrideReason: candidateEligibility(candidate).requiresOverride
          ? payload.overrideReason.trim()
          : null,
      }
      store.assignments.push(assignment)
      ticket.currentAssignment = assignment
    }
    ticket.status = TICKET_WORKFLOW_ACTIONS[action].targetStatus
    ticket.version += 1
    ticket.updatedAt = changedAt
    ticket.statusHistory.push({
      id: Math.max(...ticket.statusHistory.map((entry) => entry.id)) + 1,
      fromStatus,
      toStatus: ticket.status,
      transitionAction: workflowHistoryAction(action, fromStatus),
      reason: payload.reason?.trim() ?? null,
      changedBy: { id: actor.id, fullName: actor.fullName },
      changedAt,
    })
  }

  return {
    handlers,
    get store() {
      return store
    },
    reset() {
      store = createWorkflowFixtures()
      scenario = null
    },
    failNextAction(status) {
      if (![400, 403, 404, 409].includes(status))
        throw new Error('Unsupported mock scenario')
      scenario = status
    },
  }
}
