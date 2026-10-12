import { useQuery } from '@tanstack/react-query'
import { adminUserApi } from '../api/adminUserApi'
import { useAuth } from '../../auth/context/useAuth'

export const adminUserKeys = {
  all: ['admin', 'users'],
  lists: () => [...adminUserKeys.all, 'list'],
  list: (params) => [...adminUserKeys.lists(), params],
  details: () => [...adminUserKeys.all, 'detail'],
  detail: (id) => [...adminUserKeys.details(), String(id)],
}

export function useAdminUsers(params = {}) {
  const { isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: adminUserKeys.list(params),
    queryFn: () => adminUserApi.list(params),
    enabled: isAuthenticated && !isChecking,
    staleTime: 5000,
  })
}

export function useAdminUser(userId) {
  const { isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: adminUserKeys.detail(userId),
    queryFn: () => adminUserApi.getById(userId),
    enabled: isAuthenticated && !isChecking && Boolean(userId),
    staleTime: 5000,
  })
}
