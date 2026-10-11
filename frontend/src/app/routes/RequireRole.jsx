import { Outlet } from 'react-router'
import { useAuth } from '../../features/auth/context/useAuth'
import { SessionCheckingSpinner } from '../../shared/components/SessionCheckingSpinner'
import { ForbiddenPage } from '../../shared/components/ForbiddenPage'

export function RequireRole({ roles }) {
  const { user, isChecking } = useAuth()

  if (isChecking) {
    return <SessionCheckingSpinner />
  }

  const hasRequiredRole =
    user?.roles.some((role) => roles.includes(role)) ?? false

  return hasRequiredRole ? <Outlet /> : <ForbiddenPage />
}
