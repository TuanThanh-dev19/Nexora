import axios from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const TOKEN_KEY = 'nexora.session.token'

describe('httpClient', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.resetModules()
  })

  it('injects Bearer token from sessionStorage into requests', async () => {
    sessionStorage.setItem(TOKEN_KEY, 'test-token-123')
    const { httpClient } = await import('./httpClient')

    const config = { headers: {} }
    const interceptor = httpClient.interceptors.request.handlers[0]
    const result = interceptor.fulfilled(config)

    expect(result.headers['Authorization']).toBe('Bearer test-token-123')
  })

  it('does not inject Authorization header when not authenticated', async () => {
    const { httpClient } = await import('./httpClient')

    const config = { headers: {} }
    const interceptor = httpClient.interceptors.request.handlers[0]
    const result = interceptor.fulfilled(config)

    expect(result.headers['Authorization']).toBeUndefined()
  })

  it('dispatches nexora:unauthorized event on 401 response', async () => {
    const { httpClient } = await import('./httpClient')

    const listener = vi.fn()
    window.addEventListener('nexora:unauthorized', listener)

    const interceptor = httpClient.interceptors.response.handlers[0]
    const axiosError = { response: { status: 401 }, isAxiosError: true }
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)

    await interceptor.rejected(axiosError).catch(() => {})
    expect(listener).toHaveBeenCalledTimes(1)

    window.removeEventListener('nexora:unauthorized', listener)
  })

  it('withCredentials is false', async () => {
    const { httpClient } = await import('./httpClient')
    expect(httpClient.defaults.withCredentials).toBe(false)
  })
})
