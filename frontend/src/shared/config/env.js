export const env = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
}

export function isTicketWorkflowMockEnabled() {
  return (
    import.meta.env.DEV && import.meta.env.VITE_TICKET_WORKFLOW_MOCK === 'true'
  )
}

export function isAdminUserMockEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_ADMIN_USER_MOCK === 'true'
}

export function isDashboardMockEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_DASHBOARD_MOCK === 'true'
}

export function isTechnicianMockEnabled() {
  return (
    import.meta.env.DEV &&
    import.meta.env.VITE_TECHNICIAN_MOCK === 'true'
  )
}

