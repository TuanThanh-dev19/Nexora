import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../auth/context/useAuth'
import { ticketApi } from '../api/ticketApi'
import { isPositiveId } from './useTicketSearchParams'
import { retryTicketQuery, ticketViewerKey } from './ticketQueryOptions'

export function useTicket(ticketId) {
  const { user, isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: ['tickets', ...ticketViewerKey(user), 'detail', ticketId],
    queryFn: ({ signal }) => ticketApi.getById(ticketId, signal),
    enabled: isAuthenticated && !isChecking && isPositiveId(ticketId),
    staleTime: 0,
    retry: retryTicketQuery,
  })
}
