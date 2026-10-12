import { beforeEach, describe, expect, it } from 'vitest'
import { adminUserMockAdapter, resetMockUsers } from './adminUserMock'

describe('adminUserMockAdapter', () => {
  beforeEach(() => {
    resetMockUsers()
  })

  it('lists users with pagination and filtering', async () => {
    const all = await adminUserMockAdapter.listUsers({ page: 0, size: 20 })
    expect(all.content.length).toBe(7)
    expect(all.totalElements).toBe(7)

    const adminsOnly = await adminUserMockAdapter.listUsers({ role: 'ADMIN' })
    expect(adminsOnly.content.every((u) => u.roles.includes('ADMIN'))).toBe(true)

    const activeOnly = await adminUserMockAdapter.listUsers({ status: 'ACTIVE' })
    expect(activeOnly.content.every((u) => u.status === 'ACTIVE')).toBe(true)

    const paged = await adminUserMockAdapter.listUsers({ page: 0, size: 3 })
    expect(paged.content.length).toBe(3)
    expect(paged.totalPages).toBe(3)
  })

  it('gets user by id and throws 404 for non-existent user', async () => {
    const user = await adminUserMockAdapter.getUserById(1)
    expect(user.fullName).toBe('Trần Quản Trị')

    await expect(adminUserMockAdapter.getUserById(999)).rejects.toMatchObject({
      response: { status: 404 },
    })
  })

  it('creates new user and rejects duplicate email with 422', async () => {
    const newUser = await adminUserMockAdapter.createUser({
      email: 'newuser@nexora.campus',
      fullName: 'Người Mới',
      roles: ['REQUESTER'],
    })

    expect(newUser.id).toBeGreaterThan(7)
    expect(newUser.email).toBe('newuser@nexora.campus')
    expect(newUser.status).toBe('ACTIVE')

    await expect(
      adminUserMockAdapter.createUser({
        email: 'newuser@nexora.campus',
        fullName: 'Người Trùng',
        roles: ['REQUESTER'],
      }),
    ).rejects.toMatchObject({
      response: { status: 422 },
    })
  })

  it('updates status and rejects version mismatch with 409', async () => {
    const updated = await adminUserMockAdapter.updateUserStatus(1, {
      status: 'LOCKED',
      version: 0,
    })

    expect(updated.status).toBe('LOCKED')
    expect(updated.version).toBe(1)

    // Stale write
    await expect(
      adminUserMockAdapter.updateUserStatus(1, {
        status: 'ACTIVE',
        version: 0,
      }),
    ).rejects.toMatchObject({
      response: { status: 409 },
    })
  })

  it('updates roles and increments version', async () => {
    const updated = await adminUserMockAdapter.updateUserRoles(1, {
      roles: ['ADMIN', 'MANAGER'],
    })

    expect(updated.roles).toEqual(['ADMIN', 'MANAGER'])
    expect(updated.version).toBe(1)
  })
})
