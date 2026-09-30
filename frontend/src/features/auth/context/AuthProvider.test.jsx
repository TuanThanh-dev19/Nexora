import { render, screen, fireEvent } from '@testing-library/react'
import { act } from 'react'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

const TOKEN_KEY = 'nexora.session.token'
const USER_KEY = 'nexora.session.user'

const MOCK_TOKEN = 'mock.jwt.token'
const MOCK_USER = {
  id: 1,
  email: 'user@example.test',
  displayName: 'Test User',
  roles: ['REQUESTER'],
}

function SessionProbe() {
  const { user, accessToken, isAuthenticated, logout } = useAuth()
  return (
    <>
      <span data-testid="auth-state">{isAuthenticated ? 'authenticated' : 'anonymous'}</span>
      <span data-testid="token">{accessToken ?? 'none'}</span>
      <span data-testid="roles">{user?.roles?.join(',') ?? 'none'}</span>
      <button onClick={logout}>Logout</button>
    </>
  )
}

describe('AuthProvider', () => {
  beforeEach(() => sessionStorage.clear())

  describe('Token restoration', () => {
    it('restores authenticated session from sessionStorage', () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      sessionStorage.setItem(USER_KEY, JSON.stringify(MOCK_USER))

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      expect(screen.getByTestId('auth-state')).toHaveTextContent('authenticated')
      expect(screen.getByTestId('token')).toHaveTextContent(MOCK_TOKEN)
      expect(screen.getByTestId('roles')).toHaveTextContent('REQUESTER')
    })

    it('starts unauthenticated when sessionStorage is empty', () => {
      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )
      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    })

    it('clears malformed session and starts unauthenticated', () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      sessionStorage.setItem(USER_KEY, 'INVALID_JSON{{{')

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    })
  })

  describe('Logout', () => {
    it('clears sessionStorage and auth state on logout', () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      sessionStorage.setItem(USER_KEY, JSON.stringify(MOCK_USER))

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )
      fireEvent.click(screen.getByText('Logout'))

      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
      expect(sessionStorage.getItem(USER_KEY)).toBeNull()
    })

    it('clears session on nexora:unauthorized event', async () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      sessionStorage.setItem(USER_KEY, JSON.stringify(MOCK_USER))

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      await act(async () => {
        window.dispatchEvent(new Event('nexora:unauthorized'))
      })

      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    })
  })
})
