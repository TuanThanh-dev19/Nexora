import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getCurrentUserApi } from '../api/authApi'
import { AuthContext } from './auth-context'

const TOKEN_KEY = 'nexora.session.token'

export function AuthProvider({ children }) {
  const queryClient = useQueryClient()
  const clearPrivateCache = useCallback(() => {
    void queryClient.cancelQueries()
    queryClient.clear()
  }, [queryClient])
  const [session, setSession] = useState({ token: null, user: null })
  const [sessionError, setSessionError] = useState(null)
  const [restoreAttempt, setRestoreAttempt] = useState(0)
  /**
   * isChecking = true khi đang verify token lúc khởi động tab.
   * ProtectedRoute và RequireRole phải kiểm tra flag này để tránh
   * nháy sai màn hình login trước khi session restore hoàn tất.
   */
  const [isChecking, setIsChecking] = useState(
    () => sessionStorage.getItem(TOKEN_KEY) !== null,
  )
  const retrySession = useCallback(() => {
    setSessionError(null)
    setIsChecking(true)
    setRestoreAttempt((value) => value + 1)
  }, [])

  // ---------------------------------------------------------------------------
  // Restore session khi tab mở lại trong cùng phiên
  // ---------------------------------------------------------------------------
  useEffect(() => {
    let active = true
    clearPrivateCache()
    const token = sessionStorage.getItem(TOKEN_KEY)

    if (!token) {
      // Không có token — xác nhận ngay là chưa đăng nhập
      return
    }

    // Token tồn tại → gọi /auth/me để verify và lấy user mới nhất từ DB
    // httpClient tự gắn Authorization: Bearer từ sessionStorage
    getCurrentUserApi()
      .then((user) => {
        if (active && sessionStorage.getItem(TOKEN_KEY) === token)
          setSession({ token, user })
      })
      .catch((error) => {
        if (!active || sessionStorage.getItem(TOKEN_KEY) !== token) return
        const status = error?.response?.status
        if (status !== 401 && status !== 403) {
          setSessionError(error)
          return
        }
        // Token hết hạn hoặc user bị disable → xóa session
        sessionStorage.removeItem(TOKEN_KEY)
        setSession({ token: null, user: null })
      })
      .finally(() => {
        if (active) setIsChecking(false)
      })
    return () => {
      active = false
    }
  }, [clearPrivateCache, restoreAttempt])

  // ---------------------------------------------------------------------------
  // Logout
  // ---------------------------------------------------------------------------
  const logout = useCallback(() => {
    clearPrivateCache()
    sessionStorage.removeItem(TOKEN_KEY)
    setSession({ token: null, user: null })
    setSessionError(null)
    setIsChecking(false)
  }, [clearPrivateCache])

  // ---------------------------------------------------------------------------
  // Login: gọi sau khi backend trả accessToken + user
  // Không lưu user vào sessionStorage — user luôn được fetch từ /auth/me
  // khi restore session, đảm bảo roles là mới nhất từ DB.
  // ---------------------------------------------------------------------------
  const login = useCallback(
    ({ accessToken, user }) => {
      clearPrivateCache()
      sessionStorage.setItem(TOKEN_KEY, accessToken)
      setSession({ token: accessToken, user })
      setSessionError(null)
      setIsChecking(false)
    },
    [clearPrivateCache],
  )

  // ---------------------------------------------------------------------------
  // Lắng nghe sự kiện 401 global từ httpClient interceptor
  // ---------------------------------------------------------------------------
  useEffect(() => {
    window.addEventListener('nexora:unauthorized', logout)
    return () => window.removeEventListener('nexora:unauthorized', logout)
  }, [logout])

  const value = useMemo(
    () => ({
      user: session.user,
      accessToken: session.token,
      isAuthenticated: session.token !== null && session.user !== null,
      isChecking,
      sessionError,
      retrySession,
      login,
      logout,
    }),
    [session, isChecking, sessionError, retrySession, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
