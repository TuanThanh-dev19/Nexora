import { delay, http, HttpResponse } from 'msw'
import { PRIORITIES } from '../../../shared/constants/priorities'
import { TICKET_STATUSES } from '../constants/ticketStatuses'
import { WORK_IMAGE_LIMIT, WORK_IMAGE_TYPES } from '../constants/technicianWork'
import {
  canManageTickets,
  availableTicketActions,
  isTicketOwner,
  workflowRequiresReason,
  TICKET_WORKFLOW_ACTIONS,
  workflowHistoryAction,
} from '../constants/ticketWorkflow'
import {
  canReadInternalComments,
  canCreateComment,
  canEditComment,
  canSubmitFeedback,
} from '../constants/ticketCollaboration'
import {
  validateComment,
  validateFeedback,
} from '../schemas/ticketCollaborationValidation'
import {
  candidateEligibility,
  validateWorkflowPayload,
} from '../schemas/ticketWorkflowValidation'
import {
  validVersion,
  validateResolution,
  validateWorkLog,
} from '../schemas/technicianWorkValidation'
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
  adaptTechnicianIds = false,
  adaptReporterId = false,
} = {}) {
  let store = createWorkflowFixtures()
  let scenario = null
  const adaptedIds = new Set()
  let reporterBound = false
  const subresourceFailures = new Map()
  const adaptReporter = (actor) => {
    if (!adaptReporterId || reporterBound || !actor.roles.includes('REQUESTER'))
      return
    reporterBound = true
    const reporter = { id: actor.id, fullName: actor.fullName }
    // Bind the first Requester to demo ownership; leave ticket 18 owned by another reporter.
    for (const ticket of store.tickets) {
      if (ticket.id === 18) {
        ticket.reporter = {
          id: String(actor.id) === '4' ? 94 : 4,
          fullName: 'Người báo khác',
        }
        ticket.statusHistory[0].changedBy = ticket.reporter
        continue
      }
      ticket.reporter = reporter
      ticket.statusHistory.forEach((entry) => {
        if (['SUBMIT', 'CLOSE'].includes(entry.transitionAction))
          entry.changedBy = reporter
      })
      ticket.attachments.forEach((attachment) => {
        if (attachment.uploadedBy.id === 4) attachment.uploadedBy = reporter
      })
    }
    store.comments.forEach((comment) => {
      if (comment.author.id === 4) comment.author = reporter
    })
  }
  const adaptTechnician = (actor) => {
    if (!adaptTechnicianIds || !actor.roles.includes('TECHNICIAN')) return
    if (
      store.candidates.some((candidate) => candidate.technicianId === actor.id)
    ) {
      if ([21, 24].includes(actor.id)) adaptedIds.add(actor.id)
      return
    }
    const originalId = [21, 24].find((id) => !adaptedIds.has(id))
    if (!originalId) return
    adaptedIds.add(originalId)
    const candidate = store.candidates.find(
      (item) => item.technicianId === originalId,
    )
    candidate.technicianId = actor.id
    candidate.fullName = actor.fullName
    store.assignments.forEach((assignment) => {
      if (assignment.technician.technicianId === originalId) {
        assignment.technician.technicianId = actor.id
        assignment.technician.fullName = actor.fullName
      }
    })
    store.tickets.forEach((ticket) =>
      ticket.statusHistory.forEach((entry) => {
        if (entry.changedBy?.id === originalId) {
          entry.changedBy.id = actor.id
          entry.changedBy.fullName = actor.fullName
        }
      }),
    )
    store.workLogs.forEach((log) => {
      if (log.technician.id === originalId) {
        log.technician.id = actor.id
        log.technician.fullName = actor.fullName
      }
    })
  }
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
    adaptTechnician(actor)
    adaptReporter(actor)
    return { actor }
  }
  const ticketEndpoint = `${baseUrl.replace(/\/$/, '')}/tickets`
  const workTicket = (id) =>
    store.tickets.find((ticket) => String(ticket.id) === id)
  const workAccess = (ticket, actor) =>
    actor.roles.includes('TECHNICIAN') &&
    ticket.currentAssignment?.endedAt === null &&
    String(ticket.currentAssignment.technician.technicianId) ===
      String(actor.id)
  const workFailure = (ticket) => {
    if (!scenario) return null
    const failure = scenario
    scenario = null
    if (failure === 409) ticket.version += 1
    if (failure === 403) {
      ticket.currentAssignment.endedAt = new Date().toISOString()
      ticket.currentAssignment = null
    }
    return problem(
      failure,
      failure === 409
        ? 'stale-write'
        : failure === 403
          ? 'access-denied'
          : 'resource-not-found',
      'Lỗi mô phỏng theo kịch bản.',
    )
  }
  const changedAt = (ticket) =>
    new Date(
      Math.max(Date.now(), Date.parse(ticket.updatedAt) + 1),
    ).toISOString()
  const actionResult = (ticket) => ({
    id: ticket.id,
    status: ticket.status,
    version: ticket.version,
    currentAssignment: ticket.currentAssignment,
  })
  const workActor = async (request) => {
    const auth = await authenticate(request)
    if (auth.error) return auth
    if (!auth.actor.roles.includes('TECHNICIAN'))
      return {
        error: problem(
          403,
          'access-denied',
          'Chỉ kỹ thuật viên được thao tác.',
        ),
      }
    return auth
  }
  const workWrite = async (request, params, state) => {
    if (actionDelay) await delay(actionDelay)
    const auth = await workActor(request)
    if (auth.error) return auth
    const ticket = workTicket(params.ticketId)
    if (!ticket || !visible(ticket, auth.actor))
      return {
        error: problem(404, 'resource-not-found', 'Không thể truy cập phiếu.'),
      }
    if (scenario) return { error: workFailure(ticket) }
    if (!workAccess(ticket, auth.actor))
      return {
        error: problem(
          422,
          'business-rule-violation',
          'Bạn không còn là người được phân công.',
        ),
      }
    if (ticket.status !== state)
      return {
        error: problem(
          422,
          'business-rule-violation',
          'Trạng thái phiếu không phù hợp.',
        ),
      }
    return { ticket, actor: auth.actor }
  }
  const parseBody = async (request, fields) => {
    let body
    try {
      body = await request.json()
    } catch {
      return { error: problem(400, 'malformed-request', 'JSON không hợp lệ.') }
    }
    if (
      !body ||
      typeof body !== 'object' ||
      Array.isArray(body) ||
      Object.keys(body).some((key) => !fields.includes(key))
    )
      return {
        error: problem(
          400,
          'validation-failed',
          'Trường dữ liệu không hợp lệ.',
        ),
      }
    return { body }
  }
  const subresourceAccess = async (request, ticketId, resource) => {
    if (actionDelay) await delay(actionDelay)
    const auth = await authenticate(request)
    if (auth.error) return auth
    const ticket = workTicket(ticketId)
    if (!ticket || !visible(ticket, auth.actor))
      return {
        error: problem(404, 'resource-not-found', 'Không thể truy cập phiếu.'),
      }
    if (subresourceFailures.has(resource)) {
      const status = subresourceFailures.get(resource)
      subresourceFailures.delete(resource)
      if (status === 'invalid-image')
        return {
          error: new HttpResponse('Invalid PNG content', {
            headers: { 'Content-Type': 'image/png' },
          }),
        }
      return {
        error:
          status === 'network'
            ? HttpResponse.error()
            : problem(
                status,
                'subresource-failure',
                'Lỗi mô phỏng của dữ liệu bổ sung.',
                status === 400
                  ? {
                      [resource === 'feedback' ? 'rating' : 'content']:
                        'Dữ liệu không hợp lệ.',
                    }
                  : undefined,
              ),
      }
    }
    return { ticket, actor: auth.actor }
  }
  const handlers = [
    http.get(
      `${ticketEndpoint}/:ticketId/attachments/:attachmentId/content`,
      async ({ request, params }) => {
        const access = await subresourceAccess(
          request,
          params.ticketId,
          'attachments',
        )
        if (access.error) return access.error
        const attachment = access.ticket.attachments.find(
          (item) => String(item.id) === params.attachmentId,
        )
        const content =
          store.attachmentContents[`${params.ticketId}:${params.attachmentId}`]
        if (!attachment || !content)
          return problem(404, 'resource-not-found', 'Tệp không còn khả dụng.')
        return new HttpResponse(new Uint8Array(content.bytes), {
          headers: {
            'Content-Type': content.contentType,
            'Content-Length': String(content.bytes.length),
            'Content-Disposition': `attachment; filename="image"; filename*=UTF-8''${encodeURIComponent(attachment.originalName)}`,
          },
        })
      },
    ),
    http.get(
      `${ticketEndpoint}/:ticketId/comments`,
      async ({ request, params }) => {
        const access = await subresourceAccess(
          request,
          params.ticketId,
          'comments',
        )
        if (access.error) return access.error
        const query = queryParameters(request, ['page', 'size'])
        if (query.error) return query.error
        const comments = store.comments
          .filter(
            (comment) =>
              comment.ticketId === access.ticket.id &&
              (comment.visibility === 'PUBLIC' ||
                canReadInternalComments(access.actor)),
          )
          .sort(
            (a, b) =>
              Date.parse(a.createdAt) - Date.parse(b.createdAt) ||
              a.id.localeCompare(b.id),
          )
          .map(({ ticketId: _ticketId, ...comment }) => comment)
        return pageResponse(comments, query.page, query.size)
      },
    ),
    http.post(
      `${ticketEndpoint}/:ticketId/comments`,
      async ({ request, params }) => {
        const access = await subresourceAccess(
          request,
          params.ticketId,
          'comments',
        )
        if (access.error) return access.error
        const parsed = await parseBody(request, ['content', 'visibility'])
        if (parsed.error) return parsed.error
        const errors = validateComment(parsed.body)
        if (Object.keys(errors).length)
          return problem(
            400,
            'validation-failed',
            'Bình luận không hợp lệ.',
            errors,
          )
        if (
          !canCreateComment(access.ticket, access.actor, parsed.body.visibility)
        )
          return problem(403, 'access-denied', 'Không có quyền thêm bình luận.')
        const comment = {
          id: crypto.randomUUID(),
          author: { id: access.actor.id, fullName: access.actor.fullName },
          content: parsed.body.content.trim(),
          visibility: parsed.body.visibility,
          createdAt: changedAt(access.ticket),
          editedAt: null,
        }
        store.comments.push({ ...comment, ticketId: access.ticket.id })
        return HttpResponse.json(comment, { status: 201 })
      },
    ),
    http.put(
      `${ticketEndpoint}/:ticketId/comments/:commentId`,
      async ({ request, params }) => {
        const access = await subresourceAccess(
          request,
          params.ticketId,
          'comments',
        )
        if (access.error) return access.error
        const comment = store.comments.find(
          (item) =>
            item.ticketId === access.ticket.id && item.id === params.commentId,
        )
        if (
          !comment ||
          (comment.visibility === 'INTERNAL' &&
            !canReadInternalComments(access.actor))
        )
          return problem(
            404,
            'resource-not-found',
            'Không thể truy cập bình luận.',
          )
        if (!canEditComment(access.ticket, access.actor, comment))
          return problem(403, 'access-denied', 'Không có quyền sửa bình luận.')
        const parsed = await parseBody(request, ['content'])
        if (parsed.error) return parsed.error
        const errors = validateComment(parsed.body, true)
        if (Object.keys(errors).length)
          return problem(
            400,
            'validation-failed',
            'Bình luận không hợp lệ.',
            errors,
          )
        comment.content = parsed.body.content.trim()
        comment.editedAt = new Date(
          Math.max(
            Date.now(),
            Date.parse(comment.createdAt),
            Date.parse(comment.editedAt ?? comment.createdAt),
          ),
        ).toISOString()
        const { ticketId: _ticketId, ...result } = comment
        return HttpResponse.json(result)
      },
    ),
    http.get(
      `${ticketEndpoint}/:ticketId/feedback`,
      async ({ request, params }) => {
        const access = await subresourceAccess(
          request,
          params.ticketId,
          'feedback',
        )
        if (access.error) return access.error
        const feedback = store.feedback.find(
          (item) => item.ticketId === access.ticket.id,
        )
        if (!feedback) return HttpResponse.json(null)
        const { ticketId: _ticketId, ...result } = feedback
        return HttpResponse.json(result)
      },
    ),
    http.post(
      `${ticketEndpoint}/:ticketId/feedback`,
      async ({ request, params }) => {
        const access = await subresourceAccess(
          request,
          params.ticketId,
          'feedback',
        )
        if (access.error) return access.error
        if (!canSubmitFeedback(access.ticket, access.actor))
          return problem(
            403,
            'access-denied',
            'Chỉ người báo có thể đánh giá phiếu đã đóng.',
          )
        const parsed = await parseBody(request, ['rating', 'comment'])
        if (parsed.error) return parsed.error
        const errors = validateFeedback(parsed.body)
        if (Object.keys(errors).length)
          return problem(
            400,
            'validation-failed',
            'Đánh giá không hợp lệ.',
            errors,
          )
        if (store.feedback.some((item) => item.ticketId === access.ticket.id))
          return problem(409, 'feedback-exists', 'Phiếu đã có đánh giá.')
        const feedback = {
          id: crypto.randomUUID(),
          rating: parsed.body.rating,
          comment: parsed.body.comment?.trim() || null,
          createdAt: changedAt(access.ticket),
        }
        store.feedback.push({ ...feedback, ticketId: access.ticket.id })
        return HttpResponse.json(feedback, { status: 201 })
      },
    ),
    http.get(
      `${baseUrl.replace(/\/$/, '')}/technicians/me/assigned-tickets`,
      async ({ request }) => {
        const auth = await workActor(request)
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
          (status && !['ASSIGNED', 'IN_PROGRESS'].includes(status)) ||
          (priority && !PRIORITIES.includes(priority)) ||
          (locationId && !/^[1-9]\d*$/.test(locationId))
        )
          return problem(400, 'unsupported-filter', 'Bộ lọc không hợp lệ.')
        const content = store.tickets
          .filter(
            (ticket) =>
              workAccess(ticket, auth.actor) &&
              (!status || ticket.status === status) &&
              (!priority || ticket.priority === priority) &&
              (!locationId || String(ticket.location.id) === locationId),
          )
          .sort(
            (a, b) =>
              (direction.toLowerCase() === 'desc' ? -1 : 1) *
                String(a[sort]).localeCompare(String(b[sort])) || a.id - b.id,
          )
          .map(
            ({
              description: _description,
              version: _version,
              formVersion: _formVersion,
              dynamicValues: _dynamicValues,
              attachments: _attachments,
              statusHistory: _history,
              ...summary
            }) => summary,
          )
        return pageResponse(content, page, size)
      },
    ),
    http.get(
      `${ticketEndpoint}/:ticketId/work-logs`,
      async ({ request, params }) => {
        const auth = await authenticate(request)
        if (auth.error) return auth.error
        const ticket = workTicket(params.ticketId)
        if (!ticket || !visible(ticket, auth.actor))
          return problem(404, 'resource-not-found', 'Không thể truy cập phiếu.')
        return HttpResponse.json(
          store.workLogs
            .filter((log) => log.ticketId === ticket.id)
            .sort(
              (a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id,
            ),
        )
      },
    ),
    http.post(
      `${ticketEndpoint}/:ticketId/start`,
      async ({ request, params }) => {
        const access = await workWrite(request, params, 'ASSIGNED')
        if (access.error) return access.error
        const parsed = await parseBody(request, ['version'])
        if (parsed.error) return parsed.error
        const { ticket, actor } = access
        if (!validVersion(parsed.body.version))
          return problem(400, 'validation-failed', 'Phiên bản không hợp lệ.', {
            version: 'Không hợp lệ',
          })
        if (parsed.body.version !== ticket.version)
          return problem(409, 'stale-write', 'Phiếu đã thay đổi.')
        const at = changedAt(ticket)
        ticket.currentAssignment.acceptedAt = at
        ticket.status = 'IN_PROGRESS'
        ticket.version += 1
        ticket.updatedAt = at
        ticket.statusHistory.push({
          id: ticket.id * 1000 + ticket.statusHistory.length,
          fromStatus: 'ASSIGNED',
          toStatus: 'IN_PROGRESS',
          transitionAction: 'START_WORK',
          reason: null,
          changedBy: { id: actor.id, fullName: actor.fullName },
          changedAt: at,
        })
        return HttpResponse.json(actionResult(ticket))
      },
    ),
    http.post(
      `${ticketEndpoint}/:ticketId/work-logs`,
      async ({ request, params }) => {
        const access = await workWrite(request, params, 'IN_PROGRESS')
        if (access.error) return access.error
        const parsed = await parseBody(request, [
          'version',
          'logType',
          'content',
          'startedAt',
          'endedAt',
          'minutesSpent',
        ])
        if (parsed.error) return parsed.error
        const errors = validateWorkLog(parsed.body)
        if (Object.keys(errors).length)
          return problem(
            400,
            'validation-failed',
            'Nhật ký không hợp lệ.',
            errors,
          )
        const { ticket, actor } = access
        if (parsed.body.version !== ticket.version)
          return problem(409, 'stale-write', 'Phiếu đã thay đổi.')
        const at = changedAt(ticket)
        const log = {
          id: Math.max(0, ...store.workLogs.map((item) => item.id)) + 1,
          ticketId: ticket.id,
          technician: { id: actor.id, fullName: actor.fullName },
          ...parsed.body,
          content: parsed.body.content.trim(),
          endedAt: parsed.body.endedAt || null,
          minutesSpent: parsed.body.minutesSpent ?? null,
          createdAt: at,
        }
        delete log.version
        store.workLogs.push(log)
        ticket.version += 1
        ticket.updatedAt = at
        return HttpResponse.json(
          { ...log, ticketVersion: ticket.version },
          { status: 201 },
        )
      },
    ),
    http.post(
      `${ticketEndpoint}/:ticketId/attachments`,
      async ({ request, params }) => {
        const access = await workWrite(request, params, 'IN_PROGRESS')
        if (access.error) return access.error
        let form
        try {
          form = await request.formData()
        } catch {
          return problem(400, 'malformed-request', 'Multipart không hợp lệ.')
        }
        const files = form.getAll('files')
        let metadata
        try {
          metadata = JSON.parse(form.get('metadata'))
        } catch {
          return problem(400, 'validation-failed', 'Metadata không hợp lệ.')
        }
        if (
          metadata?.attachmentType !== 'WORK_RESULT' ||
          !files.length ||
          files.length > 5 ||
          files.some(
            (file) =>
              !WORK_IMAGE_TYPES.includes(file.type) ||
              file.size < 1 ||
              file.size > WORK_IMAGE_LIMIT,
          )
        )
          return problem(400, 'validation-failed', 'Ảnh kết quả không hợp lệ.')
        const { ticket, actor } = access
        const at = changedAt(ticket)
        const attachments = files.map((file, index) => ({
          id:
            Math.max(0, ...ticket.attachments.map((item) => item.id)) +
            index +
            1,
          fieldDefinitionId: null,
          attachmentType: 'WORK_RESULT',
          originalName: file.name,
          contentType: file.type,
          sizeBytes: file.size,
          uploadedBy: { id: actor.id, fullName: actor.fullName },
          createdAt: at,
        }))
        for (const [index, file] of files.entries()) {
          store.attachmentContents[`${ticket.id}:${attachments[index].id}`] = {
            bytes: Array.from(new Uint8Array(await file.arrayBuffer())),
            contentType: file.type,
          }
        }
        ticket.attachments.push(...attachments)
        return HttpResponse.json(attachments, { status: 201 })
      },
    ),
    http.post(
      `${ticketEndpoint}/:ticketId/resolve`,
      async ({ request, params }) => {
        const access = await workWrite(request, params, 'IN_PROGRESS')
        if (access.error) return access.error
        const parsed = await parseBody(request, [
          'version',
          'resolutionOutcome',
          'resolutionSummary',
        ])
        if (parsed.error) return parsed.error
        const { ticket, actor } = access
        const errors = validateResolution(
          parsed.body,
          store.workLogs.filter((log) => log.ticketId === ticket.id).length,
        )
        if (errors.workLogs)
          return problem(422, 'business-rule-violation', errors.workLogs)
        if (Object.keys(errors).length)
          return problem(
            400,
            'validation-failed',
            'Kết quả không hợp lệ.',
            errors,
          )
        if (parsed.body.version !== ticket.version)
          return problem(409, 'stale-write', 'Phiếu đã thay đổi.')
        const at = changedAt(ticket)
        ticket.currentAssignment.endedAt = at
        ticket.currentAssignment = null
        ticket.status = 'RESOLVED'
        ticket.resolvedAt = at
        ticket.resolutionOutcome = parsed.body.resolutionOutcome
        ticket.resolutionSummary = parsed.body.resolutionSummary.trim()
        ticket.version += 1
        ticket.updatedAt = at
        ticket.statusHistory.push({
          id: ticket.id * 1000 + ticket.statusHistory.length,
          fromStatus: 'IN_PROGRESS',
          toStatus: 'RESOLVED',
          transitionAction: 'RESOLVE',
          reason: null,
          changedBy: { id: actor.id, fullName: actor.fullName },
          changedAt: at,
        })
        return HttpResponse.json(actionResult(ticket))
      },
    ),
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
          const ticket = store.tickets.find(
            (item) => String(item.id) === params.ticketId,
          )
          if (!ticket || !visible(ticket, auth.actor))
            return problem(
              404,
              'resource-not-found',
              'Không thể truy cập phiếu.',
            )
          if (
            !canManageTickets(auth.actor) &&
            !(
              isTicketOwner(ticket, auth.actor) &&
              ['cancel', 'close', 'reopen'].includes(action)
            )
          )
            return problem(403, 'access-denied', 'Không có quyền xử lý phiếu.')
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
            ...(definition.requiresReason || action === 'close'
              ? ['reason']
              : []),
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
          const errors = validateWorkflowPayload(
            action,
            payload,
            candidate,
            workflowRequiresReason(action, ticket, auth.actor),
          )
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
              const competingManager = canManageTickets(auth.actor)
                ? auth.actor
                : WORKFLOW_MOCK_MANAGER
              if (ticket.status === 'SUBMITTED')
                applyAction(ticket, 'review', {}, competingManager)
              else if (['UNDER_REVIEW', 'REOPENED'].includes(ticket.status))
                applyAction(
                  ticket,
                  'assign',
                  { overrideReason: 'Phân công đồng thời trong demo' },
                  competingManager,
                  candidates().find(
                    (candidate) => !candidateEligibility(candidate).blocked,
                  ),
                )
              else if (['ASSIGNED', 'IN_PROGRESS'].includes(ticket.status))
                applyAction(
                  ticket,
                  'unassign',
                  { reason: 'Gỡ phân công đồng thời trong demo' },
                  competingManager,
                )
              else if (ticket.status === 'RESOLVED')
                applyAction(
                  ticket,
                  'close',
                  { reason: 'Đóng đồng thời trong demo' },
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
          if (!availableTicketActions(ticket, auth.actor).includes(action))
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
    if (action === 'close') ticket.closedAt = changedAt
    if (action === 'reopen') {
      ticket.resolvedAt = null
      ticket.slaDueAt = new Date(
        Date.parse(changedAt) + store.slaMinutes[ticket.id] * 60000,
      ).toISOString()
    }
    ticket.version += 1
    ticket.updatedAt = changedAt
    ticket.statusHistory.push({
      id: Math.max(...ticket.statusHistory.map((entry) => entry.id)) + 1,
      fromStatus,
      toStatus: ticket.status,
      transitionAction: workflowHistoryAction(
        action,
        fromStatus,
        ticket,
        actor,
      ),
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
      adaptedIds.clear()
      reporterBound = false
      subresourceFailures.clear()
    },
    failNextAction(status) {
      if (![400, 403, 404, 409].includes(status))
        throw new Error('Unsupported mock scenario')
      scenario = status
    },
    failNextSubresource(resource, status) {
      if (
        !['comments', 'feedback', 'attachments'].includes(resource) ||
        (![400, 401, 403, 404, 409, 503, 'network'].includes(status) &&
          !(resource === 'attachments' && status === 'invalid-image'))
      )
        throw new Error('Unsupported subresource scenario')
      subresourceFailures.set(resource, status)
    },
  }
}
