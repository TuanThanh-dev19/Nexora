import { expect, it } from 'vitest'
import { navigationForRoles } from './navigation'

const paths = (roles) => navigationForRoles(roles).map((item) => item.path)
it('shows no protected links to anonymous users', () =>
  expect(paths([])).toEqual([]))
it('shows requester create action only with REQUESTER', () => {
  expect(paths(['REQUESTER'])).toContain('/tickets/new')
  expect(paths(['ADMIN'])).not.toContain('/tickets/new')
  expect(paths(['REQUESTER'])).not.toContain('/assets')
})
it('uses the union for mixed roles without duplicate links', () => {
  const roles = ['ADMIN', 'REQUESTER', 'TECHNICIAN']
  const union = [...new Set(roles.flatMap((role) => paths([role])))].sort()
  expect(paths(roles).sort()).toEqual(union)
  expect(paths(roles)).toContain('/technicians/me')
  expect(paths(roles)).toContain('/my-work')
})
it('manager sees workforce/assets but not admin-only catalog/users', () => {
  expect(paths(['MANAGER'])).toEqual([
    '/dashboard',
    '/tickets',
    '/technicians',
    '/assets',
  ])
})
