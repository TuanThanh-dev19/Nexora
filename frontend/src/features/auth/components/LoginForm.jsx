import { zodResolver } from '@hookform/resolvers/zod'
import { Button, Form } from 'react-bootstrap'
import { useForm } from 'react-hook-form'
import { loginSchema } from '../schemas/authSchema'

export function LoginForm({ onSubmit }) {
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
          isInvalid={Boolean(errors.email)}
          {...register('email')}
        />
        <Form.Control.Feedback type="invalid">
          {errors.email?.message}
        </Form.Control.Feedback>
      </Form.Group>

      <Form.Group className="mb-3" controlId="login-password">
        <Form.Label>Mật khẩu</Form.Label>
        <Form.Control
          type="password"
          isInvalid={Boolean(errors.password)}
          {...register('password')}
        />
        <Form.Control.Feedback type="invalid">
          {errors.password?.message}
        </Form.Control.Feedback>
      </Form.Group>

      <Button type="submit" disabled={isSubmitting} id="btn-login">
        {isSubmitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
      </Button>
    </Form>
  )
}
