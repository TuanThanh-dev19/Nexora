import { useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Button, Col, Form, InputGroup, Modal, Row } from 'react-bootstrap'
import { Controller, useForm } from 'react-hook-form'
import { FiEye, FiEyeOff, FiUserPlus } from 'react-icons/fi'
import { ROLE_OPTIONS } from '../../../shared/constants/roles'
import { formatAdminUserError } from '../api/adminUserApi'
import { createUserSchema } from '../schemas/userSchemas'

export function CreateUserModal({ show, onHide, onSubmit, isPending = false, serverError = null }) {
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      fullName: '',
      email: '',
      password: '',
      confirmPassword: '',
      roles: ['REQUESTER'],
    },
  })

  const handleClose = () => {
    if (isPending) return
    reset({
      fullName: '',
      email: '',
      password: '',
      confirmPassword: '',
      roles: ['REQUESTER'],
    })
    setShowPassword(false)
    setShowConfirmPassword(false)
    onHide()
  }

  const handleFormSubmit = (data) => {
    // Only send backend contract fields; confirmPassword is stripped
    const payload = {
      fullName: data.fullName.trim(),
      email: data.email.trim(),
      password: data.password,
      roles: data.roles,
    }
    onSubmit(payload)
  }

  return (
    <Modal
      show={show}
      onHide={handleClose}
      backdrop="static"
      size="lg"
      centered
    >

      <Modal.Header closeButton={!isPending}>
        <Modal.Title as="h2" className="h5 mb-0 d-flex align-items-center gap-2">
          <FiUserPlus aria-hidden="true" />
          <span>Tạo tài khoản người dùng</span>
        </Modal.Title>
      </Modal.Header>
      <Form onSubmit={handleSubmit(handleFormSubmit)} noValidate>
        <Modal.Body>
          {serverError && (
            <Alert variant="danger" className="mb-3">
              {formatAdminUserError(serverError)}
            </Alert>
          )}

          <fieldset disabled={isPending}>
            <Row className="g-3">
              <Form.Group as={Col} md={6} controlId="user-fullName">
                <Form.Label>Họ và tên <span className="text-danger">*</span></Form.Label>
                <Form.Control
                  placeholder="Ví dụ: Nguyễn Văn An"
                  isInvalid={Boolean(errors.fullName)}
                  {...register('fullName')}
                />
                <Form.Control.Feedback type="invalid">
                  {errors.fullName?.message}
                </Form.Control.Feedback>
              </Form.Group>

              <Form.Group as={Col} md={6} controlId="user-email">
                <Form.Label>Email <span className="text-danger">*</span></Form.Label>
                <Form.Control
                  type="email"
                  placeholder="tennguoidung@campus.edu"
                  isInvalid={Boolean(errors.email)}
                  {...register('email')}
                />
                <Form.Control.Feedback type="invalid">
                  {errors.email?.message}
                </Form.Control.Feedback>
              </Form.Group>

              <Form.Group as={Col} md={6} controlId="user-password">
                <Form.Label>Mật khẩu khởi tạo <span className="text-danger">*</span></Form.Label>
                <InputGroup hasValidation>
                  <Form.Control
                    type={showPassword ? 'text' : 'password'}
                    placeholder="Tối thiểu 6 ký tự"
                    autoComplete="new-password"
                    isInvalid={Boolean(errors.password)}
                    {...register('password')}
                  />
                  <Button
                    variant="outline-secondary"
                    type="button"
                    aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    onClick={() => setShowPassword((v) => !v)}
                  >
                    {showPassword ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
                  </Button>
                  <Form.Control.Feedback type="invalid">
                    {errors.password?.message}
                  </Form.Control.Feedback>
                </InputGroup>
              </Form.Group>

              <Form.Group as={Col} md={6} controlId="user-confirmPassword">
                <Form.Label>Xác nhận mật khẩu <span className="text-danger">*</span></Form.Label>
                <InputGroup hasValidation>
                  <Form.Control
                    type={showConfirmPassword ? 'text' : 'password'}
                    placeholder="Nhập lại mật khẩu"
                    autoComplete="new-password"
                    isInvalid={Boolean(errors.confirmPassword)}
                    {...register('confirmPassword')}
                  />
                  <Button
                    variant="outline-secondary"
                    type="button"
                    aria-label={showConfirmPassword ? 'Ẩn xác nhận mật khẩu' : 'Hiện xác nhận mật khẩu'}
                    onClick={() => setShowConfirmPassword((v) => !v)}
                  >
                    {showConfirmPassword ? <FiEyeOff aria-hidden="true" /> : <FiEye aria-hidden="true" />}
                  </Button>
                  <Form.Control.Feedback type="invalid">
                    {errors.confirmPassword?.message}
                  </Form.Control.Feedback>
                </InputGroup>
              </Form.Group>

              <Form.Group as={Col} xs={12} controlId="user-roles">
                <Form.Label>Vai trò được cấp <span className="text-danger">*</span></Form.Label>
                <div className="p-3 border rounded bg-light">
                  <Controller
                    name="roles"
                    control={control}
                    render={({ field }) => (
                      <Row className="g-2">
                        {ROLE_OPTIONS.map(({ value, label }) => {
                          const checked = field.value?.includes(value) ?? false
                          return (
                            <Col sm={6} md={3} key={value}>
                              <Form.Check
                                id={`role-check-${value}`}
                                label={label}
                                value={value}
                                checked={checked}
                                onChange={(e) => {
                                  if (e.target.checked) {
                                    field.onChange([...(field.value ?? []), value])
                                  } else {
                                    field.onChange((field.value ?? []).filter((r) => r !== value))
                                  }
                                }}
                              />
                            </Col>
                          )
                        })}
                      </Row>
                    )}
                  />
                </div>
                {errors.roles && (
                  <div className="text-danger small mt-1">
                    {errors.roles.message}
                  </div>
                )}
                <Form.Text className="text-muted">
                  Người dùng có thể được gán nhiều vai trò cùng lúc.
                </Form.Text>
              </Form.Group>
            </Row>
          </fieldset>
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="outline-secondary"
            disabled={isPending}
            onClick={onHide}
          >
            Hủy
          </Button>
          <Button
            type="submit"
            variant="primary"
            disabled={isPending}
            id="btn-create-user-submit"
          >
            {isPending ? 'Đang tạo…' : 'Tạo tài khoản'}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  )
}
