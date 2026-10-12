import { createContext } from 'react'

/**
 * Authentication context shape:
 * {
 *   user: { id, email, fullName, status, roles } | null,
 *   accessToken: string | null,
 *   isAuthenticated: boolean,
 *   isChecking: boolean,   // true khi đang verify token lúc app khởi động
 *   login: ({ accessToken, user }) => void,
 *   logout: () => void,
 * }
 */
export const AuthContext = createContext(null)
