import { createContext } from 'react'

/**
 * Authentication context shape:
 * {
 *   user: { id, email, displayName, roles } | null,
 *   accessToken: string | null,
 *   isAuthenticated: boolean,
 *   login: ({ accessToken, user }) => void,
 *   logout: () => void,
 * }
 */
export const AuthContext = createContext(null)
