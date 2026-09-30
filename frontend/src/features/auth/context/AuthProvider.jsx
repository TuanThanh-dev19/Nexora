import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthContext } from './auth-context'

const TOKEN_KEY = 'nexora.session.token'
const USER_KEY = 'nexora.session.user'

function readSession() {
  const token = sessionStorage.getItem(TOKEN_KEY)
  const rawUser = sessionStorage.getItem(USER_KEY)
  if (!token || !rawUser) return { token: null, user: null }

  try {
    const user = JSON.parse(rawUser)
    // Minimal sanity check — server is authoritative for role claims
    if (
      user &&
      typeof user.id !== 'undefined' &&
      typeof user.email === 'string' &&
      Array.isArray(user.roles)
    ) {
      return { token, user }
    }
  } catch {
    // Malformed session → treat as unauthenticated
  }

  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
  return { token: null, user: null }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(readSession)

  const logout = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
    setSession({ token: null, user: null })
  }, [])

  /**
   * Called after the backend returns a valid token + user profile.
   * @param {{ accessToken: string, user: object }} payload
   */
  const login = useCallback(({ accessToken, user }) => {
    sessionStorage.setItem(TOKEN_KEY, accessToken)
    sessionStorage.setItem(USER_KEY, JSON.stringify(user))
    setSession({ token: accessToken, user })
  }, [])

  useEffect(() => {
    window.addEventListener('nexora:unauthorized', logout)
    return () => window.removeEventListener('nexora:unauthorized', logout)
  }, [logout])

  const value = useMemo(
    () => ({
      user: session.user,
      accessToken: session.token,
      isAuthenticated: session.token !== null,
      login,
      logout,
    }),
    [session, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
