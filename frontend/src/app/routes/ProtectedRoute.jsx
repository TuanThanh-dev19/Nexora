import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '../../features/auth/context/useAuth'
import { SessionCheckingSpinner } from '../../shared/components/SessionCheckingSpinner'
import { SessionRestoreError } from '../../shared/components/SessionRestoreError'

export function ProtectedRoute() {
  const { isAuthenticated, isChecking, sessionError } = useAuth()
  const location = useLocation()

  if (isChecking) {
    return <SessionCheckingSpinner />
  }

  if (sessionError) return <SessionRestoreError />

  return isAuthenticated ? (
    <Outlet />
  ) : (
    <Navigate
      to="/login"
      replace
      state={{ from: location.pathname + location.search }}
    />
  )
}
