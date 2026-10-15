import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { technicianApi } from '../api/technicianApi'
import { AuthContext } from '../../auth/context/auth-context'
import { TechnicianDetailPage } from './TechnicianDetailPage'

vi.mock('../api/technicianApi', () => ({
  technicianApi: {
    getById: vi.fn(),
    updateCapacity: vi.fn(),
    getSkills: vi.fn(),
    updateSkills: vi.fn(),
    getLocations: vi.fn(),
    updateServiceAreas: vi.fn(),
  },
}))

vi.mock('../../../shared/config/env', () => ({
  isTechnicianMockEnabled: vi.fn(() => false),
}))

const mockDetail = {
  technicianId: 3,
  fullName: 'Nguyễn Kỹ Thuật',
  email: 'tech1@nexora.campus',
  status: 'ACTIVE',
  roles: ['TECHNICIAN'],
  available: true,
  maxActiveTickets: 5,
  version: 0,
  skills: [
    {
      skillId: 1,
      skillCode: 'ELEC',
      skillName: 'Điện dân dụng',
      proficiencyLevel: 4,
    },
  ],
  serviceAreas: [
    {
      locationId: 1,
      locationCode: 'CS1',
      locationName: 'Cơ sở 1 (Khu A)',
      type: 'CAMPUS',
    },
  ],
}

const mockCatalogSkills = [
  { id: 1, code: 'ELEC', name: 'Điện dân dụng', active: true },
  { id: 2, code: 'HVAC', name: 'Điều hòa không khí', active: true },
]

const mockLocations = [
  { id: 1, code: 'CS1', name: 'Cơ sở 1 (Khu A)', type: 'CAMPUS', parentId: null, active: true },
  { id: 5, code: 'CS2', name: 'Cơ sở 2 (Khu B)', type: 'CAMPUS', parentId: null, active: true },
]

describe('TechnicianDetailPage', () => {
  let queryClient

  beforeEach(() => {
    vi.clearAllMocks()
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    technicianApi.getById.mockResolvedValue(mockDetail)
    technicianApi.getSkills.mockResolvedValue(mockCatalogSkills)
    technicianApi.getLocations.mockResolvedValue(mockLocations)
  })

  function renderPage({
    roles = ['MANAGER'],
    path = '/technicians/3',
    routePath = '/technicians/:technicianId',
    readOnly = false,
  } = {}) {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider
          value={{
            isAuthenticated: true,
            isChecking: false,
            user: { id: 2, fullName: 'Manager User', roles },
          }}
        >
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route
                path={routePath}
                element={<TechnicianDetailPage readOnly={readOnly} />}
              />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  it('renders all 3 configuration panels with identity, coverage callout, and initial values', async () => {
    technicianApi.getById.mockResolvedValueOnce(mockDetail)

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Hồ sơ: Nguyễn Kỹ Thuật/ })).toBeInTheDocument()
      expect(screen.getByText('tech1@nexora.campus')).toBeInTheDocument()
    })

    // Panel 1: Profile & Capacity
    expect(screen.getByRole('heading', { name: 'Hồ sơ & Tính sẵn sàng' })).toBeInTheDocument()
    const capacityInput = screen.getByLabelText(/Sức chứa tối đa/)
    expect(capacityInput).toHaveValue(5)

    // Panel 2: Skills
    expect(screen.getByRole('heading', { name: 'Kỹ năng chuyên môn' })).toBeInTheDocument()
    expect(screen.getByText('Điện dân dụng')).toBeInTheDocument()
    expect(screen.getByText('ELEC')).toBeInTheDocument()

    // Panel 3: Service Areas with mandatory coverage callout
    expect(screen.getByRole('heading', { name: /Vùng phục vụ/ })).toBeInTheDocument()
    expect(
      screen.getByText(/Quy tắc bao hàm vùng phục vụ \(Coverage\):/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/tự động bao gồm tất cả các vùng con/),
    ).toBeInTheDocument()
    expect(screen.getAllByText('Cơ sở 1 (Khu A)').length).toBeGreaterThan(0)
  })

  it('validates capacity input: requires positive integer and blocks saving when invalid', async () => {
    technicianApi.getById.mockResolvedValueOnce(mockDetail)

    renderPage()

    await waitFor(() => {
      expect(screen.getByLabelText(/Sức chứa tối đa/)).toHaveValue(5)
    })

    const capacityInput = screen.getByLabelText(/Sức chứa tối đa/)
    const saveButton = screen.getByRole('button', { name: /Lưu cấu hình/ })

    // Change to 0 (invalid)
    fireEvent.change(capacityInput, { target: { value: '0' } })
    fireEvent.blur(capacityInput)

    expect(
      screen.getByText('Sức chứa tối đa phải là số nguyên lớn hơn 0.'),
    ).toBeInTheDocument()
    expect(saveButton).toBeDisabled()

    // Change to negative
    fireEvent.change(capacityInput, { target: { value: '-2' } })
    expect(saveButton).toBeDisabled()

    // Change to valid value 8
    fireEvent.change(capacityInput, { target: { value: '8' } })
    expect(
      screen.queryByText('Sức chứa tối đa phải là số nguyên lớn hơn 0.'),
    ).not.toBeInTheDocument()
    expect(saveButton).toBeEnabled()

    // Save
    technicianApi.updateCapacity.mockResolvedValueOnce({
      ...mockDetail,
      maxActiveTickets: 8,
      version: 1,
    })
    fireEvent.click(saveButton)

    await waitFor(() => {
      expect(technicianApi.updateCapacity).toHaveBeenCalledWith(3, {
        available: true,
        maxActiveTickets: 8,
        version: 0,
      })
      expect(
        screen.getByText('Đã cập nhật tính sẵn sàng và sức chứa tối đa.'),
      ).toBeInTheDocument()
    })
  })

  it('adds and updates skills in the skills panel', async () => {
    technicianApi.getById.mockResolvedValueOnce(mockDetail)

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Kỹ năng chuyên môn' })).toBeInTheDocument()
    })

    // Select skill from catalog
    const skillSelect = await screen.findByLabelText(/Chọn kỹ năng/)
    fireEvent.change(skillSelect, { target: { value: '2' } }) // HVAC

    const proficiencySelect = screen.getByLabelText(/Mức độ thành thạo/)
    fireEvent.change(proficiencySelect, { target: { value: '5' } }) // Level 5

    // Click Add
    const addButton = screen.getByRole('button', { name: /^Thêm$/ })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('Điều hòa không khí')).toBeInTheDocument()
    })

    // Save skills
    technicianApi.updateSkills.mockResolvedValueOnce({
      ...mockDetail,
      skills: [
        ...mockDetail.skills,
        { skillId: 2, skillCode: 'HVAC', skillName: 'Điều hòa không khí', proficiencyLevel: 5 },
      ],
      version: 1,
    })

    const saveSkillsBtn = screen.getByRole('button', { name: /Lưu danh sách kỹ năng/ })
    fireEvent.click(saveSkillsBtn)

    await waitFor(() => {
      expect(technicianApi.updateSkills).toHaveBeenCalledWith(3, {
        skills: [
          { skillId: 1, proficiencyLevel: 4 },
          { skillId: 2, proficiencyLevel: 5 },
        ],
        version: 0,
      })
      expect(
        screen.getByText('Đã cập nhật danh sách kỹ năng thành công.'),
      ).toBeInTheDocument()
    })
  })

  it('handles 409 Conflict with ConflictAlert and reload button', async () => {
    technicianApi.getById.mockResolvedValueOnce(mockDetail)

    renderPage()

    await waitFor(() => {
      expect(screen.getByLabelText(/Sức chứa tối đa/)).toHaveValue(5)
    })

    const capacityInput = screen.getByLabelText(/Sức chứa tối đa/)
    fireEvent.change(capacityInput, { target: { value: '10' } })

    const conflictErr = new Error('Stale version.')
    conflictErr.response = { status: 409, data: { detail: 'Stale version.' } }
    technicianApi.updateCapacity.mockRejectedValueOnce(conflictErr)

    fireEvent.click(screen.getByRole('button', { name: /Lưu cấu hình/ }))

    await waitFor(() => {
      expect(screen.getByText(/Xung đột phiên bản \(409 Conflict\)/)).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /Tải lại dữ liệu/ })).toBeInTheDocument()
    })

    // Click Reload
    technicianApi.getById.mockResolvedValueOnce({
      ...mockDetail,
      maxActiveTickets: 7,
      version: 2,
    })
    fireEvent.click(screen.getByRole('button', { name: /Tải lại dữ liệu/ }))

    await waitFor(() => {
      expect(technicianApi.getById).toHaveBeenCalledTimes(2)
      expect(screen.queryByText(/Xung đột phiên bản \(409 Conflict\)/)).not.toBeInTheDocument()
    })
  })

  it('renders read-only view when viewing /technicians/me as a technician', async () => {
    technicianApi.getById.mockResolvedValueOnce(mockDetail)

    renderPage({
      roles: ['TECHNICIAN'],
      path: '/technicians/me',
      routePath: '/technicians/me',
      readOnly: true,
    })

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Hồ sơ kỹ thuật của tôi' })).toBeInTheDocument()
      expect(screen.getByText('Chế độ chỉ đọc')).toBeInTheDocument()
      expect(screen.getByText('← Tổng quan')).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Hồ sơ & Tính sẵn sàng' })).toBeInTheDocument()
    })

    // Edit controls, add buttons, and save buttons must NOT be present
    expect(screen.queryByRole('button', { name: /Lưu cấu hình/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Lưu danh sách kỹ năng/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Lưu vùng phục vụ/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Chọn kỹ năng/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Thêm$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Xóa/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Bỏ chọn/ })).not.toBeInTheDocument()

    // Switch is disabled
    const switchEl = screen.getByRole('checkbox', { name: /Sẵn sàng/ })
    expect(switchEl).toBeDisabled()

    // Coverage callout is still shown
    expect(
      screen.getByText(/Quy tắc bao hàm vùng phục vụ \(Coverage\):/),
    ).toBeInTheDocument()
  })
})
