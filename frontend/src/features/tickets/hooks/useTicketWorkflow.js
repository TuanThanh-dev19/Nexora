import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { isTicketWorkflowMockEnabled } from '../../../shared/config/env'
import { useAuth } from '../../auth/context/useAuth'
import { ticketApi } from '../api/ticketApi'
import {
  availableTicketActions,
  canManageTickets,
} from '../constants/ticketWorkflow'
import { retryTicketQuery, ticketViewerKey } from './ticketQueryOptions'

export function useTicketWorkflow() {
  const { user, isAuthenticated, isChecking } = useAuth()
  const client = useQueryClient()
  const enabled = isAuthenticated && !isChecking
  const isActionEnabled = (action) =>
    enabled &&
    (['review', 'assign'].includes(action) || isTicketWorkflowMockEnabled())
  const refresh = () =>
    Promise.all([
      client.invalidateQueries({
        queryKey: ['tickets', ...ticketViewerKey(user)],
      }),
      client.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
  const mutation = useMutation({
    mutationFn: ({ action, ticketId, payload, ticket }) => {
      if (
        !isActionEnabled(action) ||
        !availableTicketActions(ticket, user).includes(action)
      )
        throw new Error('Chức năng xử lý phiếu chưa được hỗ trợ.')
      return ticketApi[action](ticketId, payload)
    },
    retry: false,
    onSuccess: refresh,
    onError: (error, { action }) => {
      if (
        ['assign', 'reassign'].includes(action) &&
        error?.response?.status === 422
      )
        return client.invalidateQueries({
          queryKey: [
            'tickets',
            ...ticketViewerKey(user),
            'eligible-technicians',
          ],
        })
    },
  })
  return { enabled, isActionEnabled, mutation, refresh }
}

export function useEligibleTechnicians(ticketId, params, open) {
  const { user, isAuthenticated, isChecking } = useAuth()
  return useQuery({
    queryKey: [
      'tickets',
      ...ticketViewerKey(user),
      'eligible-technicians',
      String(ticketId),
      params,
    ],
    queryFn: ({ signal }) =>
      ticketApi.eligibleTechnicians(ticketId, params, signal),
    enabled: open && isAuthenticated && !isChecking && canManageTickets(user),
    staleTime: 0,
    retry: retryTicketQuery,
  })
}
