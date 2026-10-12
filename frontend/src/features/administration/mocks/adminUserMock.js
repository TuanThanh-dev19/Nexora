import { ROLE_CODES } from '../../../shared/constants/roles'
import { USER_STATUS_CODES } from '../../../shared/constants/userStatuses'

// Deterministic initial mock users for demo/development
const INITIAL_MOCK_USERS = [
  {
    id: 1,
    email: 'admin@nexora.campus',
    fullName: 'Trần Quản Trị',
    status: USER_STATUS_CODES.ACTIVE,
    roles: [ROLE_CODES.ADMIN],
    createdAt: '2026-09-01T08:00:00Z',
    updatedAt: '2026-09-01T08:00:00Z',
    version: 0,
  },
  {
    id: 2,
    email: 'manager@nexora.campus',
    fullName: 'Lê Quản Lý',
    status: USER_STATUS_CODES.ACTIVE,
    roles: [ROLE_CODES.MANAGER],
    createdAt: '2026-09-02T09:30:00Z',
    updatedAt: '2026-09-02T09:30:00Z',
    version: 0,
  },
  {
    id: 3,
    email: 'tech1@nexora.campus',
    fullName: 'Nguyễn Kỹ Thuật',
    status: USER_STATUS_CODES.ACTIVE,
    roles: [ROLE_CODES.TECHNICIAN],
    createdAt: '2026-09-05T10:15:00Z',
    updatedAt: '2026-09-05T10:15:00Z',
    version: 0,
  },
  {
    id: 4,
    email: 'requester1@nexora.campus',
    fullName: 'Phạm Người Dùng',
    status: USER_STATUS_CODES.ACTIVE,
    roles: [ROLE_CODES.REQUESTER],
    createdAt: '2026-09-10T14:20:00Z',
    updatedAt: '2026-09-10T14:20:00Z',
    version: 0,
  },
  {
    id: 5,
    email: 'locked.user@nexora.campus',
    fullName: 'Vũ Tạm Khóa',
    status: USER_STATUS_CODES.LOCKED,
    roles: [ROLE_CODES.REQUESTER],
    createdAt: '2026-09-15T11:00:00Z',
    updatedAt: '2026-09-15T11:00:00Z',
    version: 0,
  },
  {
    id: 6,
    email: 'disabled.user@nexora.campus',
    fullName: 'Đỗ Vô Hiệu',
    status: USER_STATUS_CODES.DISABLED,
    roles: [ROLE_CODES.REQUESTER],
    createdAt: '2026-09-18T16:45:00Z',
    updatedAt: '2026-09-18T16:45:00Z',
    version: 0,
  },
  {
    id: 7,
    email: 'tech.manager@nexora.campus',
    fullName: 'Hoàng Đa Năng',
    status: USER_STATUS_CODES.ACTIVE,
    roles: [ROLE_CODES.TECHNICIAN, ROLE_CODES.MANAGER],
    createdAt: '2026-09-20T08:30:00Z',
    updatedAt: '2026-09-20T08:30:00Z',
    version: 0,
  },
]

let mockUsers = JSON.parse(JSON.stringify(INITIAL_MOCK_USERS))
let nextUserId = 8

export function resetMockUsers() {
  mockUsers = JSON.parse(JSON.stringify(INITIAL_MOCK_USERS))
  nextUserId = 8
}

export const adminUserMockAdapter = {
  async listUsers(params = {}) {
    const page = Number(params.page ?? 0)
    const size = Number(params.size ?? 20)
    let filtered = [...mockUsers]

    if (params.status) {
      filtered = filtered.filter((u) => u.status === params.status)
    }
    if (params.role) {
      filtered = filtered.filter((u) => u.roles.includes(params.role))
    }

    // Default sort by id desc or createdAt desc
    filtered.sort((a, b) => b.id - a.id)

    const totalElements = filtered.length
    const totalPages = Math.ceil(totalElements / size) || 0
    const start = page * size
    const content = filtered.slice(start, start + size)

    return {
      content,
      page,
      size,
      totalElements,
      totalPages,
    }
  },

  async getUserById(userId) {
    const id = Number(userId)
    const user = mockUsers.find((u) => u.id === id)
    if (!user) {
      const error = new Error(`User not found: ${userId}`)
      error.response = { status: 404, data: { detail: `User not found: ${userId}` } }
      throw error
    }
    return JSON.parse(JSON.stringify(user))
  },

  async createUser({ email, fullName, roles }) {
    const normalized = email.trim().toLowerCase()
    const exists = mockUsers.some((u) => u.email.trim().toLowerCase() === normalized)
    if (exists) {
      const error = new Error(`Email already in use: ${email}`)
      error.response = {
        status: 422,
        data: { detail: `Email already in use: ${email}` },
      }
      throw error
    }

    const newUser = {
      id: nextUserId++,
      email: email.trim(),
      fullName: fullName.trim(),
      status: USER_STATUS_CODES.ACTIVE,
      roles: [...roles].sort(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 0,
    }
    mockUsers.unshift(newUser)
    return JSON.parse(JSON.stringify(newUser))
  },

  async updateUserStatus(userId, { status, version }) {
    const id = Number(userId)
    const index = mockUsers.findIndex((u) => u.id === id)
    if (index === -1) {
      const error = new Error(`User not found: ${userId}`)
      error.response = { status: 404, data: { detail: `User not found: ${userId}` } }
      throw error
    }

    const current = mockUsers[index]
    if (version != null && current.version !== version) {
      const error = new Error('Concurrent update conflict')
      error.response = { status: 409, data: { detail: 'Concurrent update conflict' } }
      throw error
    }

    const updated = {
      ...current,
      status,
      version: current.version + 1,
      updatedAt: new Date().toISOString(),
    }
    mockUsers[index] = updated
    return JSON.parse(JSON.stringify(updated))
  },

  async updateUserRoles(userId, { roles }) {
    const id = Number(userId)
    const index = mockUsers.findIndex((u) => u.id === id)
    if (index === -1) {
      const error = new Error(`User not found: ${userId}`)
      error.response = { status: 404, data: { detail: `User not found: ${userId}` } }
      throw error
    }

    const current = mockUsers[index]
    const updated = {
      ...current,
      roles: [...roles].sort(),
      version: current.version + 1,
      updatedAt: new Date().toISOString(),
    }
    mockUsers[index] = updated
    return JSON.parse(JSON.stringify(updated))
  },
}
