import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../../auth/context/useAuth'
import { demoScope } from '../../../shared/config/demoScope'
import { ticketApi } from '../api/ticketApi'
import { ticketViewerKey, retryTicketQuery } from './ticketQueryOptions'
import {
  canCreateComment,
  canEditComment,
  canSubmitFeedback,
} from '../constants/ticketCollaboration'

export function useTicketComments(ticket, page) {
  const { user, isAuthenticated, isChecking } = useAuth()
  const client = useQueryClient()
  const available = demoScope.features.comments
  const enabled = available && isAuthenticated && !isChecking
  const key = [
    'tickets',
    ...ticketViewerKey(user),
    'comments',
    String(ticket.id),
  ]
  const query = useQuery({
    queryKey: [...key, page],
    queryFn: ({ signal }) =>
      ticketApi.comments(ticket.id, { page, size: 20 }, signal),
    enabled,
    retry: retryTicketQuery,
  })
  const mutation = useMutation({
    mutationFn: ({ payload, comment }) => {
      const permitted = comment
        ? canEditComment(ticket, user, comment)
        : canCreateComment(ticket, user, payload.visibility)
      if (!enabled || !permitted)
        throw new Error('Bình luận chưa khả dụng cho phiếu này.')
      return comment
        ? ticketApi.updateComment(ticket.id, comment.id, payload)
        : ticketApi.addComment(ticket.id, payload)
    },
    retry: false,
    onSuccess: () => client.invalidateQueries({ queryKey: key }),
  })
  return { query, mutation, available }
}

export function useTicketFeedback(ticket) {
  const { user, isAuthenticated, isChecking } = useAuth()
  const client = useQueryClient()
  const available = demoScope.features.feedback
  const enabled = available && isAuthenticated && !isChecking
  const key = [
    'tickets',
    ...ticketViewerKey(user),
    'feedback',
    String(ticket.id),
  ]
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => ticketApi.feedback(ticket.id, signal),
    enabled,
    retry: retryTicketQuery,
  })
  const mutation = useMutation({
    mutationFn: (payload) => {
      if (
        !enabled ||
        !query.isSuccess ||
        query.isFetching ||
        query.data !== null ||
        !canSubmitFeedback(ticket, user)
      )
        throw new Error('Đánh giá chưa khả dụng cho phiếu này.')
      return ticketApi.addFeedback(ticket.id, payload)
    },
    retry: false,
    onSuccess: () => client.invalidateQueries({ queryKey: key }),
    onError: (error) => {
      if (error?.response?.status === 409)
        return client.invalidateQueries({ queryKey: key })
    },
  })
  return { query, mutation, available }
}
