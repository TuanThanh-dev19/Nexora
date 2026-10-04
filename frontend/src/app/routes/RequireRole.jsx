import { Navigate, Outlet } from 'react-router'
import { useAuth } from '../../features/auth/context/useAuth'
import { SessionCheckingSpinner } from '../../shared/components/SessionCheckingSpinner'

export function RequireRole({ roles }) {
  const { user, isChecking } = useAuth()

  if (isChecking) {
    return <SessionCheckingSpinner />
  }

  const hasRequiredRole =
    user?.roles.some((role) => roles.includes(role)) ?? false

  return hasRequiredRole ? <Outlet /> : <Navigate to="/unauthorized" replace />
}
