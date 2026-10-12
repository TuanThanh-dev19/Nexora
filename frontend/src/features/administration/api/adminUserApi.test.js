import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { adminUserApi, formatAdminUserError } from './adminUserApi'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
  },
}))

vi.mock('../../../shared/config/env', () => ({
  isAdminUserMockEnabled: vi.fn(() => false),
}))

describe('adminUserApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('list: calls GET /admin/users with query parameters', async () => {
    const mockData = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const params = { page: 1, size: 10, status: 'ACTIVE', role: 'ADMIN' }
    const result = await adminUserApi.list(params)

    expect(httpClient.get).toHaveBeenCalledWith('/admin/users', { params })
    expect(result).toEqual(mockData)
  })

  it('getById: calls GET /admin/users/{userId}', async () => {
    const mockUser = { id: 5, fullName: 'Test Admin', email: 'admin@test.com' }
    httpClient.get.mockResolvedValueOnce({ data: mockUser })

    const result = await adminUserApi.getById(5)

    expect(httpClient.get).toHaveBeenCalledWith('/admin/users/5')
    expect(result).toEqual(mockUser)
  })

  it('create: calls POST /admin/users with sanitized payload and strips confirmPassword', async () => {
    const createdUser = { id: 10, fullName: 'New User', email: 'new@test.com', roles: ['REQUESTER'] }
    httpClient.post.mockResolvedValueOnce({ data: createdUser })

    const payload = {
      fullName: '  New User  ',
      email: '  new@test.com  ',
      password: 'password123',
      confirmPassword: 'password123',
      roles: ['REQUESTER'],
    }

    const result = await adminUserApi.create(payload)

    expect(httpClient.post).toHaveBeenCalledWith('/admin/users', {
      fullName: 'New User',
      email: 'new@test.com',
      password: 'password123',
      roles: ['REQUESTER'],
    })
    expect(result).toEqual(createdUser)
  })

  it('updateStatus: calls PATCH /admin/users/{userId}/status', async () => {
    const updatedUser = { id: 3, status: 'LOCKED' }
    httpClient.patch.mockResolvedValueOnce({ data: updatedUser })

    const payload = { status: 'LOCKED', version: 1 }
    const result = await adminUserApi.updateStatus(3, payload)

    expect(httpClient.patch).toHaveBeenCalledWith('/admin/users/3/status', payload)
    expect(result).toEqual(updatedUser)
  })

  it('updateRoles: calls PUT /admin/users/{userId}/roles', async () => {
    const updatedUser = { id: 3, roles: ['MANAGER', 'ADMIN'] }
    httpClient.put.mockResolvedValueOnce({ data: updatedUser })

    const payload = { roles: ['MANAGER', 'ADMIN'] }
    const result = await adminUserApi.updateRoles(3, payload)

    expect(httpClient.put).toHaveBeenCalledWith('/admin/users/3/roles', payload)
    expect(result).toEqual(updatedUser)
  })
})

describe('formatAdminUserError', () => {
  it('formats duplicate email error', () => {
    const err = { response: { status: 422, data: { detail: 'Email already in use: test@campus.edu' } } }
    expect(formatAdminUserError(err)).toBe('Email này đã được sử dụng trong hệ thống.')
  })

  it('formats concurrent update conflict', () => {
    const err = { response: { status: 409, data: { detail: 'Concurrent update conflict' } } }
    expect(formatAdminUserError(err)).toContain('Dữ liệu người dùng đã bị thay đổi bởi thao tác khác')
  })

  it('formats fieldErrors map', () => {
    const err = { response: { status: 400, data: { fieldErrors: { email: 'Email không đúng' } } } }
    expect(formatAdminUserError(err)).toBe('Email không đúng')
  })

  it('formats standard HTTP status codes', () => {
    expect(formatAdminUserError({ response: { status: 403 } })).toContain('không có quyền')
    expect(formatAdminUserError({ response: { status: 404 } })).toContain('Không tìm thấy người dùng')
    expect(formatAdminUserError({ response: { status: 401 } })).toContain('Phiên làm việc đã hết hạn')
    expect(formatAdminUserError({ response: { status: 500 } })).toContain('Lỗi hệ thống')
    expect(formatAdminUserError(new Error('Network error'))).toContain('Không thể kết nối đến máy chủ')
  })
})
