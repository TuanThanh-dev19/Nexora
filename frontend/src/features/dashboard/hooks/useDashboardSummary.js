import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../api/dashboardApi'
import { useAuth } from '../../auth/context/useAuth'
import { demoScope } from '../../../shared/config/demoScope'

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
    enabled: demoScope.features.dashboard,
    retry: false,
  })
}
