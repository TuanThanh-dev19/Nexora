import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { demoScope } from '../../../shared/config/demoScope'
import { useAuth } from '../../auth/context/useAuth'
import { ticketApi } from '../api/ticketApi'
import { canTechnicianWrite } from '../constants/technicianWork'
import { retryTicketQuery, ticketViewerKey } from './ticketQueryOptions'

export function useAssignedTickets(search) {
  const { user, isAuthenticated, isChecking } = useAuth()
  const available = demoScope.features.myWork
  const query = useQuery({
    queryKey: ['tickets', ...ticketViewerKey(user), 'assigned', search],
    queryFn: ({ signal }) => ticketApi.assignedToMe(search, signal),
    enabled:
      available &&
      isAuthenticated &&
      !isChecking &&
      user?.roles?.includes('TECHNICIAN'),
    staleTime: 0,
    retry: retryTicketQuery,
  })
  return { ...query, available }
}

export function useWorkLogs(ticketId) {
  const { user, isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: ['tickets', ...ticketViewerKey(user), 'work-logs', ticketId],
    queryFn: ({ signal }) => ticketApi.workLogs(ticketId, signal),
    enabled:
      demoScope.features.workLogs &&
      isAuthenticated &&
      !isChecking &&
      Boolean(ticketId),
    staleTime: 0,
    retry: retryTicketQuery,
  })
}

export function useTechnicianMutation(ticket) {
  const { user, isAuthenticated, isChecking } = useAuth()
  const client = useQueryClient()
  const refresh = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: ['tickets'] }),
      client.invalidateQueries({ queryKey: ['technicians'] }),
      client.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  const mutation = useMutation({
    mutationFn: ({ action, payload, files }) => {
      const source = action === 'start' ? 'ASSIGNED' : 'IN_PROGRESS'
      if (
        !isAuthenticated ||
        isChecking ||
        demoScope.actions[action] !== true ||
        !canTechnicianWrite(ticket, user, source)
      )
        throw new Error('Thao tác chưa khả dụng cho phiếu này.')
      if (action === 'uploadWorkResult')
        return ticketApi.uploadWorkResult(ticket.id, files)
      return ticketApi[action](ticket.id, payload)
    },
    retry: false,
    onSuccess: refresh,
    onError: (error) => {
      if ([403, 404, 409, 422].includes(error?.response?.status)) void refresh()
    },
  })
  return { mutation, refresh }
}
