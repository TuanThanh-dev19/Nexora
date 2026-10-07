import { createBrowserRouter, Navigate } from 'react-router'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { DashboardPage } from '../features/dashboard/pages/DashboardPage'
import { HomePage } from '../features/home/pages/HomePage'
import { TicketListPage } from '../features/tickets/pages/TicketListPage'
import { TicketDetailPage } from '../features/tickets/pages/TicketDetailPage'
import { PlaceholderPage } from '../shared/components/PlaceholderPage'
import { ROLE_CODES } from '../shared/constants/roles'
import { AppLayout } from '../shared/layouts/AppLayout'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { RequireRole } from './routes/RequireRole'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      {
        element: <ProtectedRoute />,
        children: [
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'tickets', element: <TicketListPage /> },
          { path: 'tickets/:ticketId', element: <TicketDetailPage /> },
          {
            element: (
              <RequireRole roles={[ROLE_CODES.MANAGER, ROLE_CODES.ADMIN]} />
            ),
            children: [
              {
                path: 'assets',
                element: (
                  <PlaceholderPage
                    title="Equipment & assets"
                    description="Equipment types, individual assets, locations, and status history belong here."
                  />
                ),
              },
              {
                path: 'categories',
                element: (
                  <PlaceholderPage
                    title="Dynamic categories"
                    description="Build, validate, version, and publish incident forms here."
                  />
                ),
              },
            ],
          },
          {
            element: <RequireRole roles={[ROLE_CODES.ADMIN]} />,
            children: [
              {
                path: 'administration',
                element: (
                  <PlaceholderPage
                    title="Administration"
                    description="Users, roles, permissions, and audit logs belong here."
                  />
                ),
              },
            ],
          },
        ],
      },
      {
        path: 'unauthorized',
        element: (
          <PlaceholderPage
            title="403 — Unauthorized"
            description="Your current role cannot access this page."
          />
        ),
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
])
