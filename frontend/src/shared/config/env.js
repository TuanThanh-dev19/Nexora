export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
}

export function isTicketWorkflowMockEnabled() {
  return (
    import.meta.env.DEV && import.meta.env.VITE_TICKET_WORKFLOW_MOCK === 'true'
  )
}
