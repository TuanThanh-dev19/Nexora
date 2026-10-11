import { ROLE_CODES, ROLES } from './roles'

export const MANAGEMENT_ROLES = [ROLE_CODES.MANAGER, ROLE_CODES.ADMIN]
export const NAVIGATION_ITEMS = [
  { path: '/dashboard', label: 'Tổng quan', icon: 'grid', roles: ROLES },
  {
    path: '/tickets',
    label: 'Phiếu yêu cầu',
    icon: 'tag',
    roles: ROLES,
    end: true,
  },
  {
    path: '/tickets/new',
    label: 'Tạo phiếu',
    icon: 'plus',
    roles: [ROLE_CODES.REQUESTER],
  },
  {
    path: '/my-work',
    label: 'Công việc của tôi',
    icon: 'tool',
    roles: [ROLE_CODES.TECHNICIAN],
  },
  {
    path: '/technicians/me',
    label: 'Hồ sơ kỹ thuật của tôi',
    roles: [ROLE_CODES.TECHNICIAN],
    group: 'management',
  },
  {
    path: '/technicians',
    label: 'Kỹ thuật viên',
    roles: MANAGEMENT_ROLES,
    group: 'management',
    end: true,
  },
  {
    path: '/assets',
    label: 'Tài sản',
    roles: MANAGEMENT_ROLES,
    group: 'management',
  },
  {
    path: '/categories',
    label: 'Danh mục sự cố',
    roles: [ROLE_CODES.ADMIN],
    group: 'management',
  },
  {
    path: '/administration/users',
    label: 'Người dùng',
    roles: [ROLE_CODES.ADMIN],
    group: 'management',
  },
  {
    path: '/administration/locations',
    label: 'Địa điểm',
    roles: [ROLE_CODES.ADMIN],
    group: 'management',
  },
  {
    path: '/administration/equipment-types',
    label: 'Loại thiết bị',
    roles: [ROLE_CODES.ADMIN],
    group: 'management',
  },
]

export function navigationForRoles(roles = []) {
  return NAVIGATION_ITEMS.filter((item) =>
    item.roles.some((role) => roles.includes(role)),
  )
}
