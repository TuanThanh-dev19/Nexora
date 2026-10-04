import { useCallback, useEffect, useMemo, useState } from 'react'
import { getCurrentUserApi } from '../api/authApi'
import { AuthContext } from './auth-context'

const TOKEN_KEY = 'nexora.session.token'

export function AuthProvider({ children }) {
  const [session, setSession] = useState({ token: null, user: null })
  /**
   * isChecking = true khi đang verify token lúc khởi động tab.
   * ProtectedRoute và RequireRole phải kiểm tra flag này để tránh
   * nháy sai màn hình login trước khi session restore hoàn tất.
   */
  const [isChecking, setIsChecking] = useState(true)

  // ---------------------------------------------------------------------------
  // Restore session khi tab mở lại trong cùng phiên
  // ---------------------------------------------------------------------------
  useEffect(() => {
    const token = sessionStorage.getItem(TOKEN_KEY)

    if (!token) {
      // Không có token — xác nhận ngay là chưa đăng nhập
      setIsChecking(false)
      return
    }

    // Token tồn tại → gọi /auth/me để verify và lấy user mới nhất từ DB
    // httpClient tự gắn Authorization: Bearer từ sessionStorage
    getCurrentUserApi()
      .then((user) => {
        setSession({ token, user })
      })
      .catch(() => {
        // Token hết hạn hoặc user bị disable → xóa session
        sessionStorage.removeItem(TOKEN_KEY)
        setSession({ token: null, user: null })
      })
      .finally(() => {
        setIsChecking(false)
      })
  }, []) // Chỉ chạy 1 lần khi mount

  // ---------------------------------------------------------------------------
  // Logout
  // ---------------------------------------------------------------------------
  const logout = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY)
    setSession({ token: null, user: null })
  }, [])

  // ---------------------------------------------------------------------------
  // Login: gọi sau khi backend trả accessToken + user
  // Không lưu user vào sessionStorage — user luôn được fetch từ /auth/me
  // khi restore session, đảm bảo roles là mới nhất từ DB.
  // ---------------------------------------------------------------------------
  const login = useCallback(({ accessToken, user }) => {
    sessionStorage.setItem(TOKEN_KEY, accessToken)
    setSession({ token: accessToken, user })
  }, [])

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
      login,
      logout,
    }),
    [session, isChecking, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
