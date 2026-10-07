import {
  fireEvent,
  render as testingRender,
  screen,
  waitFor,
} from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCurrentUserApi } from '../api/authApi'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './useAuth'

vi.mock('../api/authApi')

const TOKEN_KEY = 'nexora.session.token'
const MOCK_TOKEN = 'mock.jwt.token'
const MOCK_USER = {
  id: 1,
  email: 'user@example.test',
  fullName: 'Test User',
  status: 'ACTIVE',
  roles: ['REQUESTER'],
}

function render(
  ui,
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  }),
) {
  return testingRender(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  )
}

function SessionProbe() {
  const { user, accessToken, isAuthenticated, isChecking, logout, login } =
    useAuth()
  return (
    <>
      <span data-testid="is-checking">{isChecking ? 'checking' : 'done'}</span>
      <span data-testid="auth-state">
        {isAuthenticated ? 'authenticated' : 'anonymous'}
      </span>
      <span data-testid="token">{accessToken ?? 'none'}</span>
      <span data-testid="roles">{user?.roles?.join(',') ?? 'none'}</span>
      <button onClick={logout}>Logout</button>
      <button
        onClick={() =>
          login({ accessToken: 'second.token', user: { ...MOCK_USER, id: 2 } })
        }
      >
        Switch account
      </button>
    </>
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  describe('Token restoration', () => {
    it('restores authenticated session from sessionStorage via api', async () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      getCurrentUserApi.mockResolvedValueOnce(MOCK_USER)

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      expect(screen.getByTestId('is-checking')).toHaveTextContent('checking')

      await waitFor(() => {
        expect(screen.getByTestId('is-checking')).toHaveTextContent('done')
      })

      expect(screen.getByTestId('auth-state')).toHaveTextContent(
        'authenticated',
      )
      expect(screen.getByTestId('token')).toHaveTextContent(MOCK_TOKEN)
      expect(screen.getByTestId('roles')).toHaveTextContent('REQUESTER')
    })

    it('starts unauthenticated and done checking when sessionStorage is empty', () => {
      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )
      expect(screen.getByTestId('is-checking')).toHaveTextContent('done')
      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    })

    it('clears session when api returns 401', async () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      getCurrentUserApi.mockRejectedValueOnce({ response: { status: 401 } })

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      await waitFor(() => {
        expect(screen.getByTestId('is-checking')).toHaveTextContent('done')
      })

      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    })
  })

  describe('Logout', () => {
    it.each(['Logout', 'Switch account'])(
      'cancels requests and removes ticket data on %s',
      async (action) => {
        const client = new QueryClient({
          defaultOptions: { queries: { retry: false } },
        })
        sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
        getCurrentUserApi.mockResolvedValueOnce(MOCK_USER)
        render(
          <AuthProvider>
            <SessionProbe />
          </AuthProvider>,
          client,
        )
        await waitFor(() =>
          expect(screen.getByTestId('is-checking')).toHaveTextContent('done'),
        )
        client.setQueryData(['tickets', 1, 'REQUESTER', 'detail', '10'], {
          description: 'Private ticket',
        })
        let aborted = false
        const pending = client
          .fetchQuery({
            queryKey: ['tickets', 1, 'REQUESTER', 'list'],
            queryFn: ({ signal }) =>
              new Promise((resolve, reject) => {
                signal.addEventListener('abort', () => {
                  aborted = true
                  reject(new Error('cancelled'))
                })
              }),
          })
          .catch(() => undefined)
        fireEvent.click(screen.getByText(action))
        await pending
        expect(aborted).toBe(true)
        expect(client.getQueriesData({ queryKey: ['tickets'] })).toEqual([])
      },
    )

    it('removes private cache when a 401 event logs the user out', async () => {
      const client = new QueryClient()
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      getCurrentUserApi.mockResolvedValueOnce(MOCK_USER)
      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
        client,
      )
      await waitFor(() =>
        expect(screen.getByTestId('is-checking')).toHaveTextContent('done'),
      )
      client.setQueryData(['tickets', 1, 'detail', '10'], {
        description: 'Private ticket',
      })
      await act(async () =>
        window.dispatchEvent(new Event('nexora:unauthorized')),
      )
      expect(client.getQueriesData({ queryKey: ['tickets'] })).toEqual([])
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    })

    it('clears sessionStorage and auth state on logout', async () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      getCurrentUserApi.mockResolvedValueOnce(MOCK_USER)

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      await waitFor(() => {
        expect(screen.getByTestId('is-checking')).toHaveTextContent('done')
      })

      fireEvent.click(screen.getByText('Logout'))

      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
      expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull()
    })

    it('clears session on nexora:unauthorized event', async () => {
      sessionStorage.setItem(TOKEN_KEY, MOCK_TOKEN)
      getCurrentUserApi.mockResolvedValueOnce(MOCK_USER)

      render(
        <AuthProvider>
          <SessionProbe />
        </AuthProvider>,
      )

      await waitFor(() => {
        expect(screen.getByTestId('is-checking')).toHaveTextContent('done')
      })

      await act(async () => {
        window.dispatchEvent(new Event('nexora:unauthorized'))
      })

      expect(screen.getByTestId('auth-state')).toHaveTextContent('anonymous')
    })
  })
})
