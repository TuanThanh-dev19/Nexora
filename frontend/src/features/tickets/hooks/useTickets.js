import { useQuery } from '@tanstack/react-query'
import { ticketApi } from '../api/ticketApi'
import { useAuth } from '../../auth/context/useAuth'
import { retryTicketQuery, ticketViewerKey } from './ticketQueryOptions'

export function useTickets(params) {
  const { user, isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: ['tickets', ...ticketViewerKey(user), 'list', params],
    queryFn: ({ signal }) => ticketApi.search(params, signal),
    enabled: isAuthenticated && !isChecking && params != null,
    staleTime: 0,
    retry: retryTicketQuery,
  })
}
