import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { AuthContext } from '../../features/auth/context/auth-context'
import { ForbiddenPage } from './ForbiddenPage'

describe('ForbiddenPage', () => {
  it('renders 403 title, friendly explanation, user info and return button', () => {
    const mockUser = {
      fullName: 'Trần Kỹ Thuật',
      email: 'tech@campus.edu',
      roles: ['TECHNICIAN'],
    }

    render(
      <AuthContext.Provider value={{ user: mockUser }}>
        <MemoryRouter>
          <ForbiddenPage />
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('403 — Từ chối truy cập')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Bạn không có quyền truy cập trang này' })).toBeInTheDocument()
    expect(screen.getByText(/Trần Kỹ Thuật/)).toBeInTheDocument()
    expect(screen.getByText(/TECHNICIAN/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Quay lại Tổng quan/ })).toBeInTheDocument()
  })
})

