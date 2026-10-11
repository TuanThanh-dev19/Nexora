import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'bootstrap/dist/css/bootstrap.min.css'
import App from './App.jsx'
import './shared/styles/global.css'
import {
  isDashboardMockEnabled,
  isTicketWorkflowMockEnabled,
} from './shared/config/env'

async function start() {
  if (import.meta.env.DEV && isDashboardMockEnabled()) {
    const { startDashboardMock } =
      await import('./features/dashboard/mocks/browser')
    await startDashboardMock()
  } else if (import.meta.env.DEV && isTicketWorkflowMockEnabled()) {
    const { startTicketWorkflowMock } =
      await import('./features/tickets/mocks/browser')
    await startTicketWorkflowMock()
  }
  createRoot(document.getElementById('root')).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

void start()
