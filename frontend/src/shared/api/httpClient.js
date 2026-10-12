import axios from 'axios'
import { env } from '../config/env'

export const httpClient = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: false,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Inject Bearer token from sessionStorage before each request
httpClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('nexora.session.token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Clear local session on HTTP 401
httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      window.dispatchEvent(new Event('nexora:unauthorized'))
    }
    return Promise.reject(error)
  },
)
