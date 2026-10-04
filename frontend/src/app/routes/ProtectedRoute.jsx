import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../../features/auth/context/useAuth'
import { SessionCheckingSpinner } from '../../shared/components/SessionCheckingSpinner'

export function ProtectedRoute() {
  const { isAuthenticated, isChecking } = useAuth()
  const location = useLocation()

  if (isChecking) {
    return <SessionCheckingSpinner />
  }

  return isAuthenticated ? (
    <Outlet />
  ) : (
    <Navigate to="/login" replace state={{ from: location.pathname }} />
  )
}
