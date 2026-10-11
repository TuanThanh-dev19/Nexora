import { z } from 'zod'

export const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Email không được để trống')
    .email('Vui lòng nhập email hợp lệ'),
  password: z.string().min(1, 'Mật khẩu không được để trống'),
})

