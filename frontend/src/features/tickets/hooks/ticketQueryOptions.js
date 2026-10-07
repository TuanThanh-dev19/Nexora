export function ticketViewerKey(user) {
  return [user?.id ?? null, [...(user?.roles ?? [])].sort().join(',')]
}

export function retryTicketQuery(failureCount, error) {
  const status = error?.response?.status
  return failureCount < 1 && (status == null || status >= 500)
}
