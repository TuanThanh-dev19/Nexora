import { createBrowserRouter, Navigate } from 'react-router'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { DashboardPage } from '../features/dashboard/pages/DashboardPage'
import { HomePage } from '../features/home/pages/HomePage'
import { TicketListPage } from '../features/tickets/pages/TicketListPage'
import { PlaceholderPage } from '../shared/components/PlaceholderPage'
import { ROLE_CODES } from '../shared/constants/roles'
import { AppLayout } from '../shared/layouts/AppLayout'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { RequireRole } from './routes/RequireRole'
import { CategoryListPage } from '../features/categories/pages/CategoryListPage'
import { CategoryVersionsPage } from '../features/categories/pages/CategoryVersionsPage'
import { FormEditorPage } from '../features/categories/pages/FormEditorPage'

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
            ],
          },
          {
            element: <RequireRole roles={[ROLE_CODES.ADMIN]} />,
            children: [
              { path: 'categories', element: <CategoryListPage /> },
              {
                path: 'categories/:categoryId/forms',
                element: <CategoryVersionsPage />,
              },
              {
                path: 'categories/forms/:formVersionId',
                element: <FormEditorPage />,
              },
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
