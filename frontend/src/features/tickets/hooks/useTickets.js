import { useQuery } from '@tanstack/react-query'
import { ticketApi } from '../api/ticketApi'
import { useAuth } from '../../auth/context/useAuth'
import { retryTicketQuery, ticketViewerKey } from './ticketQueryOptions'

export function useTickets(requestSearch) {
  const { user, isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: ['tickets', ...ticketViewerKey(user), 'list', requestSearch],
    queryFn: ({ signal }) => ticketApi.search(requestSearch, signal),
    enabled: isAuthenticated && !isChecking,
    staleTime: 0,
    retry: retryTicketQuery,
  })
}
