import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../api/dashboardApi'
import { useAuth } from '../../auth/context/useAuth'

export function useDashboardSummary() {
  const { user } = useAuth()
  return useQuery({
    queryKey: [
      'dashboard',
      'summary',
      user?.id,
      [...(user?.roles ?? [])].sort().join(','),
    ],
    queryFn: dashboardApi.getSummary,
    retry: false,
  })
}
