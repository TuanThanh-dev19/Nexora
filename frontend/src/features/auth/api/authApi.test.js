import { describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { getCurrentUserApi, loginApi } from './authApi'

vi.mock('../../../shared/api/httpClient')

describe('authApi', () => {
  it('loginApi_sends_email_password_and_returns_response', async () => {
    const mockData = { accessToken: 'token', user: { email: 'test@test.com' } }
    httpClient.post.mockResolvedValueOnce({ data: mockData })

    const result = await loginApi('test@test.com', 'password123')

    expect(httpClient.post).toHaveBeenCalledWith('/auth/login', {
      email: 'test@test.com',
      password: 'password123',
    })
    expect(result).toEqual(mockData)
  })

  it('getCurrentUserApi_sends_get_request_to_auth_me', async () => {
    const mockData = { id: 1, email: 'test@test.com' }
    httpClient.get.mockResolvedValueOnce({ data: mockData })

    const result = await getCurrentUserApi()

    expect(httpClient.get).toHaveBeenCalledWith('/auth/me')
    expect(result).toEqual(mockData)
  })

  it('getCurrentUserApi_rejects_on_401', async () => {
    const error = new Error('Unauthorized')
    httpClient.get.mockRejectedValueOnce(error)

    await expect(getCurrentUserApi()).rejects.toThrow('Unauthorized')
  })
})
