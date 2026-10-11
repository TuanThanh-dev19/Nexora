import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Form, InputGroup } from 'react-bootstrap'
import { useForm } from 'react-hook-form'
import { FiArrowRight, FiEye, FiEyeOff } from 'react-icons/fi'
import { loginSchema } from '../schemas/authSchema'

export function LoginForm({ onSubmit }) {
  const [showPassword, setShowPassword] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  })

  return (
    <Form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Form.Group className="mb-3" controlId="login-email">
        <Form.Label>Email</Form.Label>
        <Form.Control
          type="email"
          autoComplete="username"
          placeholder="Email của bạn"
          disabled={isSubmitting}
          isInvalid={Boolean(errors.email)}
          {...register('email')}
        />
        <Form.Control.Feedback type="invalid">
          {errors.email?.message}
        </Form.Control.Feedback>
      </Form.Group>

      <Form.Group className="mb-3" controlId="login-password">
        <Form.Label>Mật khẩu</Form.Label>
        <InputGroup hasValidation>
          <Form.Control
            type={showPassword ? 'text' : 'password'}
            autoComplete="current-password"
            placeholder="Nhập mật khẩu"
            disabled={isSubmitting}
            isInvalid={Boolean(errors.password)}
            {...register('password')}
          />
          <Button
            variant="outline-secondary"
            type="button"
            disabled={isSubmitting}
            id="btn-toggle-password"
            aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            onClick={() => setShowPassword((prev) => !prev)}
          >
            {showPassword ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
          </Button>
          <Form.Control.Feedback type="invalid">
            {errors.password?.message}
          </Form.Control.Feedback>
        </InputGroup>
      </Form.Group>

      <Button
        type="submit"
        disabled={isSubmitting}
        id="btn-login"
        className="login-submit w-100"
      >
        {isSubmitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
        <FiArrowRight aria-hidden="true" />
      </Button>
    </Form>
  )
}

