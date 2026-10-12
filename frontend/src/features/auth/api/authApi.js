import { httpClient } from '../../../shared/api/httpClient'

/**
 * Gọi POST /api/v1/auth/login.
 *
 * @param {string} email
 * @param {string} password
 * @returns {Promise<{accessToken: string, tokenType: string, expiresIn: number, user: {id: number, email: string, fullName: string, roles: string[]}}>}
 */
export async function loginApi(email, password) {
  const { data } = await httpClient.post('/auth/login', { email, password })
  return data
}

/**
 * Gọi GET /api/v1/auth/me để lấy thông tin user hiện tại từ backend.
 * httpClient tự động gắn Authorization: Bearer từ sessionStorage.
 *
 * @returns {Promise<{id: number, email: string, fullName: string, status: string, roles: string[]}>}
 */
export async function getCurrentUserApi() {
  const { data } = await httpClient.get('/auth/me')
  return data
}
