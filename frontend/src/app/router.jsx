import { createBrowserRouter, Navigate } from 'react-router'
import { LoginPage } from '../features/auth/pages/LoginPage'
import { DashboardPage } from '../features/dashboard/pages/DashboardPage'
import { HomePage } from '../features/home/pages/HomePage'
import { TicketListPage } from '../features/tickets/pages/TicketListPage'
import { TicketDetailPage } from '../features/tickets/pages/TicketDetailPage'
import { MyWorkPage } from '../features/tickets/pages/MyWorkPage'
import { PendingFeaturePage } from '../shared/components/PendingFeaturePage'
import { NotFoundPage } from '../shared/components/NotFoundPage'
import { RouteErrorPage } from '../shared/components/RouteErrorPage'
import { ForbiddenPage } from '../shared/components/ForbiddenPage'
import { ROLE_CODES } from '../shared/constants/roles'
import { AppLayout } from '../shared/layouts/AppLayout'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { RequireRole } from './routes/RequireRole'
import { CategoryListPage } from '../features/categories/pages/CategoryListPage'
import { CategoryVersionsPage } from '../features/categories/pages/CategoryVersionsPage'
import { FormEditorPage } from '../features/categories/pages/FormEditorPage'
import { UserListPage } from '../features/administration/pages/UserListPage'
import { UserDetailPage } from '../features/administration/pages/UserDetailPage'

export const appRoutes = [
  {
    element: <AppLayout />,
    errorElement: <RouteErrorPage />,
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
            element: <RequireRole roles={[ROLE_CODES.REQUESTER]} />,
            children: [
              {
                path: 'tickets/new',
                element: (
                  <PendingFeaturePage title="Tạo phiếu yêu cầu" issue={57} />
                ),
              },
            ],
          },
          {
            element: <RequireRole roles={[ROLE_CODES.TECHNICIAN]} />,
            children: [
              { path: 'my-work', element: <MyWorkPage /> },
              {
                path: 'technicians/me',
                element: (
                  <PendingFeaturePage
                    title="Hồ sơ kỹ thuật của tôi"
                    issue={52}
                  />
                ),
              },
            ],
          },
          {
            element: (
              <RequireRole roles={[ROLE_CODES.MANAGER, ROLE_CODES.ADMIN]} />
            ),
            children: [
              {
                path: 'assets',
                element: <PendingFeaturePage title="Tài sản" issue={55} />,
              },
              {
                path: 'technicians',
                element: (
                  <PendingFeaturePage title="Kỹ thuật viên" issue={52} />
                ),
              },
              {
                path: 'technicians/:technicianId',
                element: (
                  <PendingFeaturePage title="Hồ sơ kỹ thuật viên" issue={52} />
                ),
              },
            ],
          },
          {
            element: <RequireRole roles={[ROLE_CODES.ADMIN]} />,
            children: [
              {
                path: 'administration/locations',
                element: <PendingFeaturePage title="Địa điểm" issue={54} />,
              },
              {
                path: 'administration/equipment-types',
                element: (
                  <PendingFeaturePage title="Loại thiết bị" issue={54} />
                ),
              },
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
                element: <Navigate to="/administration/users" replace />,
              },
              {
                path: 'administration/users',
                element: <UserListPage />,
              },
              {
                path: 'administration/users/:userId',
                element: <UserDetailPage />,
              },
            ],
          },
        ],
      },
      {
        path: 'unauthorized',
        element: <ForbiddenPage />,
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

export const router = createBrowserRouter(appRoutes)
