import { useState } from 'react'
import { Alert, Card, Container } from 'react-bootstrap'
import { useLocation, useNavigate } from 'react-router'
import { loginApi } from '../api/authApi'
import { LoginForm } from '../components/LoginForm'
import { useAuth } from '../context/useAuth'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState(null)

  const handleLogin = async (values) => {
    setError(null)
    try {
      const data = await loginApi(values.email, values.password)
      login({ accessToken: data.accessToken, user: data.user })
      const redirect = location.state?.from ?? '/dashboard'
      navigate(redirect, { replace: true })
    } catch (err) {
      if (err?.response?.status === 401) {
        setError('Email hoặc mật khẩu không đúng.')
      } else {
        setError('Lỗi kết nối. Vui lòng thử lại sau.')
      }
    }
  }

  return (
    <Container className="page-container page-narrow">
      <Card className="shadow-sm">
        <Card.Body className="p-4">
          <h1 className="h3 mb-3">Nexora — Đăng nhập</h1>
          {error && <Alert variant="danger">{error}</Alert>}
          <LoginForm onSubmit={handleLogin} />
        </Card.Body>
      </Card>
    </Container>
  )
}
