import { useState } from 'react'
import { Alert, Card, Container } from 'react-bootstrap'
import { useLocation, useNavigate } from 'react-router'
import { FiClipboard, FiShield } from 'react-icons/fi'
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
    const normalizedEmail = values.email ? values.email.trim() : ''
    try {
      const data = await loginApi(normalizedEmail, values.password)
      login({ accessToken: data.accessToken, user: data.user })
      const redirect = location.state?.from ?? '/dashboard'
      navigate(redirect, { replace: true })
    } catch (err) {
      if (err?.response?.status === 401) {
        setError('Email hoặc mật khẩu không đúng.')
      } else if (err?.response?.status === 400) {
        setError('Thông tin đăng nhập không hợp lệ.')
      } else {
        setError('Lỗi kết nối. Vui lòng thử lại sau.')
      }
    }
  }

  return (
    <Container className="page-container login-page">
      <div className="login-layout">
        <section className="login-story">
          <span className="login-story-mark">
            <img src="/logo.jpg" alt="Nexora Logo" />
          </span>
          <p className="eyebrow">NEXORA · CAMPUS CARE</p>
          <h2>
            Chăm sóc campus,
            <br />
            bắt đầu từ kết nối.
          </h2>
          <p>
            Một không gian để theo dõi sự cố và giữ những thông tin cần thiết
            luôn rõ ràng.
          </p>
          <div className="login-story-note">
            <FiClipboard aria-hidden="true" />
            <span>
              Mỗi phiếu yêu cầu là một bước chăm sóc không gian chung.
            </span>
          </div>
        </section>
        <Card className="login-card">
          <Card.Body>
            <p className="eyebrow">CHÀO MỪNG TRỞ LẠI</p>
            <h1 className="h3 mb-2">Nexora — Đăng nhập</h1>
            <p className="text-secondary login-intro">
              Tiếp tục vào không gian làm việc của bạn.
            </p>
            {error && <Alert variant="danger">{error}</Alert>}
            <LoginForm onSubmit={handleLogin} />
            <p className="login-access-note">
              <FiShield aria-hidden="true" /> Quyền truy cập được xác định theo
              tài khoản của bạn.
            </p>
          </Card.Body>
        </Card>
      </div>
    </Container>
  )
}
