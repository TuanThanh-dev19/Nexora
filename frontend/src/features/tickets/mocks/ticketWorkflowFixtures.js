export const WORKFLOW_MOCK_MANAGER = {
  id: 5,
  fullName: 'Quản lý demo',
  status: 'ACTIVE',
  roles: ['MANAGER'],
}

export function createWorkflowFixtures() {
  const location = { id: 2, code: 'R201', name: 'Phòng 201' }
  const skill = { id: 1, name: 'Thiết bị trình chiếu' }
  const candidates = [
    { technicianId: 21, fullName: 'Nguyễn An' },
    { technicianId: 22, fullName: 'Trần Bình', available: false },
    { technicianId: 23, fullName: 'Lê Chi', skillMatches: false, skills: [] },
    { technicianId: 24, fullName: 'Phạm Dũng', maxActiveTickets: 1 },
    {
      technicianId: 25,
      fullName: 'Võ Hà',
      coverageMatches: false,
      serviceAreas: [{ id: 3, name: 'Phòng 301' }],
    },
    { technicianId: 26, fullName: 'Đỗ Khánh', userStatus: 'DISABLED' },
    { technicianId: 27, fullName: 'Hoàng Lan', hasTechnicianRole: false },
    {
      technicianId: 28,
      fullName: 'Bùi Minh',
      hasProfile: false,
      maxActiveTickets: null,
      skills: [],
      serviceAreas: [],
    },
  ].map((candidate) => ({
    email: `technician${candidate.technicianId}@example.test`,
    userStatus: 'ACTIVE',
    hasTechnicianRole: true,
    hasProfile: true,
    available: true,
    maxActiveTickets: 5,
    skills: [skill],
    serviceAreas: [location],
    skillMatches: true,
    coverageMatches: true,
    ...candidate,
  }))
  const paths = {
    SUBMITTED: [],
    UNDER_REVIEW: ['BEGIN_REVIEW'],
    ASSIGNED: ['BEGIN_REVIEW', 'ASSIGN'],
    IN_PROGRESS: ['BEGIN_REVIEW', 'ASSIGN', 'START_WORK'],
    REOPENED: ['BEGIN_REVIEW', 'ASSIGN', 'START_WORK', 'RESOLVE', 'REOPEN'],
    RESOLVED: ['BEGIN_REVIEW', 'ASSIGN', 'START_WORK', 'RESOLVE'],
    REJECTED: ['REJECT'],
    CLOSED: ['BEGIN_REVIEW', 'ASSIGN', 'START_WORK', 'RESOLVE', 'CLOSE'],
    CANCELLED: ['CANCEL_BEFORE_ASSIGNMENT'],
  }
  const target = {
    BEGIN_REVIEW: 'UNDER_REVIEW',
    ASSIGN: 'ASSIGNED',
    START_WORK: 'IN_PROGRESS',
    RESOLVE: 'RESOLVED',
    REOPEN: 'REOPENED',
    REJECT: 'REJECTED',
    CLOSE: 'CLOSED',
    CANCEL_BEFORE_ASSIGNMENT: 'CANCELLED',
  }
  const assignments = []
  const tickets = Object.entries(paths).map(([status, path], index) => {
    const id = index + 10
    const createdAt = `2026-10-09T08:${String(index).padStart(2, '0')}:00+07:00`
    const reporter = { id: 4, fullName: 'Người báo demo' }
    const statusHistory = [
      {
        id: id * 100,
        fromStatus: null,
        toStatus: 'SUBMITTED',
        transitionAction: 'SUBMIT',
        reason: null,
        changedBy: reporter,
        changedAt: createdAt,
      },
    ]
    let currentAssignment = null
    path.forEach((action, step) => {
      const previous = statusHistory.at(-1)
      const changedAt = new Date(
        Date.parse(createdAt) + (step + 1) * 60000,
      ).toISOString()
      statusHistory.push({
        id: id * 100 + step + 1,
        fromStatus: previous.toStatus,
        toStatus: target[action],
        transitionAction: action,
        reason: ['REOPEN', 'REJECT', 'CANCEL_BEFORE_ASSIGNMENT'].includes(
          action,
        )
          ? 'Lý do demo'
          : null,
        changedBy: ['START_WORK', 'RESOLVE'].includes(action)
          ? {
              id: id === 13 ? 24 : 21,
              fullName: id === 13 ? 'Phạm Dũng' : 'Nguyễn An',
            }
          : action === 'CLOSE'
            ? reporter
            : {
                id: WORKFLOW_MOCK_MANAGER.id,
                fullName: WORKFLOW_MOCK_MANAGER.fullName,
              },
        changedAt,
      })
      if (action === 'ASSIGN') {
        const technician = candidates.find(
          (candidate) => candidate.technicianId === (id === 13 ? 24 : 21),
        )
        currentAssignment = {
          id: id * 10,
          ticketId: id,
          technician: {
            technicianId: technician.technicianId,
            fullName: technician.fullName,
          },
          assignedBy: {
            id: WORKFLOW_MOCK_MANAGER.id,
            fullName: WORKFLOW_MOCK_MANAGER.fullName,
          },
          assignedAt: changedAt,
          acceptedAt: null,
          endedAt: null,
          endReason: null,
          overrideReason: null,
        }
        assignments.push(currentAssignment)
      }
      if (action === 'START_WORK') currentAssignment.acceptedAt = changedAt
      if (action === 'RESOLVE') {
        currentAssignment.endedAt = changedAt
        currentAssignment = null
      }
    })
    return {
      id,
      ticketNumber: `NX-2026-${String(id).padStart(6, '0')}`,
      title: `Máy chiếu cần kiểm tra (${status})`,
      status,
      priority: 'HIGH',
      category: { id: 1, code: 'AV', name: skill.name },
      location,
      asset: { id: 3, assetCode: 'PROJ-03' },
      reporter,
      slaDueAt: '2026-10-10T08:00:00+07:00',
      createdAt,
      updatedAt: statusHistory.at(-1).changedAt,
      description: 'Phiếu demo cho review và phân công.',
      version: path.length,
      formVersion: {
        id: 20,
        versionNumber: 1,
        formStatus: 'ARCHIVED',
        fields: [],
      },
      dynamicValues: [],
      attachments: [],
      statusHistory,
      currentAssignment,
    }
  })
  const workLogs = [
    {
      id: 1,
      ticketId: 15,
      technician: { id: 21, fullName: 'Nguyễn An' },
      logType: 'REPAIR',
      content: 'Đã kiểm tra và sửa thiết bị.',
      startedAt: '2026-10-09T08:00:00+07:00',
      endedAt: '2026-10-09T08:20:00+07:00',
      minutesSpent: 20,
      createdAt: '2026-10-09T08:20:00+07:00',
    },
  ]
  return { tickets, candidates, assignments, workLogs }
}
