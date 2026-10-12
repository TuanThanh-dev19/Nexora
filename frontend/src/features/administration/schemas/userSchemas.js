import { z } from 'zod'
import { ROLE_CODES } from '../../../shared/constants/roles'
import { USER_STATUS_CODES } from '../../../shared/constants/userStatuses'

export const createUserSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, 'Họ và tên không được để trống')
      .max(120, 'Họ và tên không được vượt quá 120 ký tự'),
    email: z
      .string()
      .trim()
      .min(1, 'Email không được để trống')
      .email('Email không đúng định dạng')
      .max(254, 'Email không được vượt quá 254 ký tự'),
    password: z
      .string()
      .min(6, 'Mật khẩu phải có ít nhất 6 ký tự')
      .max(100, 'Mật khẩu không được vượt quá 100 ký tự'),
    confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu'),
    roles: z
      .array(z.nativeEnum(ROLE_CODES))
      .min(1, 'Vui lòng chọn ít nhất một vai trò'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Mật khẩu xác nhận không khớp',
    path: ['confirmPassword'],
  })

export const updateUserRolesSchema = z.object({
  roles: z
    .array(z.nativeEnum(ROLE_CODES))
    .min(1, 'Người dùng phải có ít nhất một vai trò'),
})

export const updateUserStatusSchema = z.object({
  status: z.nativeEnum(USER_STATUS_CODES, {
    errorMap: () => ({ message: 'Trạng thái không hợp lệ' }),
  }),
})
