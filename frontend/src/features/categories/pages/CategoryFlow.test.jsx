import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'
import {
  render,
  screen,
  waitFor,
  within,
  cleanup,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import {
  createMemoryRouter,
  createRoutesFromElements,
  RouterProvider,
  Route,
} from 'react-router'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { CategoryListPage } from './CategoryListPage'
import { CategoryVersionsPage } from './CategoryVersionsPage'
import { FormEditorPage } from './FormEditorPage'
import { RequireRole } from '../../../app/routes/RequireRole'
import { AuthContext } from '../../auth/context/auth-context'

const category = {
  id: 1,
  code: 'IT',
  name: 'IT support',
  active: true,
  assetPolicy: 'OPTIONAL',
  defaultPriority: 'NORMAL',
  locationRequired: true,
}
const skill = { id: 2, code: 'IT', name: 'IT technician', active: true }
const sla = { id: 3, policyName: 'Normal', resolutionMinutes: 60, active: true }
let form, savedPayload, publishCount
const server = setupServer(
  http.get('*/api/v1/admin/categories', () => HttpResponse.json([category])),
  http.get('*/api/v1/admin/categories/1/form-versions', () =>
    HttpResponse.json(form ? [form] : []),
  ),
  http.post('*/api/v1/admin/categories/1/form-versions/drafts', () => {
    form = {
      id: 10,
      category,
      versionNumber: 1,
      revision: 0,
      status: 'DRAFT',
      fields: [],
      requiresEquipment: false,
      minAttachments: 0,
      defaultSkillId: null,
      defaultSlaPolicyId: null,
      createdAt: '2026-10-07T09:00:00Z',
    }
    return HttpResponse.json(form, { status: 201 })
  }),
  http.get('*/api/v1/admin/form-versions/10', () => HttpResponse.json(form)),
  http.get('*/api/v1/admin/skills', () => HttpResponse.json([skill])),
  http.get('*/api/v1/sla-policies', () => HttpResponse.json([sla])),
  http.put('*/api/v1/admin/form-versions/10', async ({ request }) => {
    savedPayload = await request.json()
    form = {
      ...form,
      ...savedPayload,
      revision: form.revision + 1,
      defaultSkill: skill,
      defaultSlaPolicy: sla,
    }
    return HttpResponse.json(form)
  }),
  http.post('*/api/v1/admin/form-versions/10/publish', async ({ request }) => {
    const { revision } = await request.json()
    if (revision !== form.revision)
      return HttpResponse.json({ detail: 'Stale version.' }, { status: 409 })
    publishCount++
    form = {
      ...form,
      revision: revision + 1,
      status: 'PUBLISHED',
      publishedAt: '2026-10-07T10:00:00Z',
    }
    return HttpResponse.json(form)
  }),
)
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
beforeEach(() => {
  form = null
  savedPayload = null
  publishCount = 0
})

function app(path = '/categories', roles = ['ADMIN']) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={{ user: { roles }, isChecking: false }}>
        <RouterProvider
          router={createMemoryRouter(
            createRoutesFromElements(
              <>
                <Route element={<RequireRole roles={['ADMIN']} />}>
                  <Route path="/categories" element={<CategoryListPage />} />
                  <Route
                    path="/categories/:categoryId/forms"
                    element={<CategoryVersionsPage />}
                  />
                  <Route
                    path="/categories/forms/:formVersionId"
                    element={<FormEditorPage />}
                  />
                </Route>
                <Route path="/unauthorized" element={<h1>Access denied</h1>} />
              </>,
            ),
            { initialEntries: [path] },
          )}
        />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('category administration flow', () => {
  const seed = (status = 'DRAFT') => {
    form = {
      id: 10,
      category,
      versionNumber: 1,
      revision: 4,
      status,
      requiresEquipment: false,
      minAttachments: 0,
      defaultSkillId: 2,
      defaultSlaPolicyId: 3,
      fields: [
        {
          id: 1,
          fieldKey: 'summary',
          label: 'Tên trường mẫu',
          fieldType: 'TEXT',
          required: false,
          active: true,
          helpText: '',
          placeholder: '',
          displayOrder: 0,
          validationRules: {},
          options: [],
        },
      ],
    }
  }

  it('confirms navigation and warns on refresh without discarding a draft', async () => {
    seed()
    const user = userEvent.setup({ delay: null })
    app('/categories/forms/10')
    await user.type(
      await screen.findByLabelText('Nhãn', { exact: true }),
      ' changed',
    )
    const unload = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(unload)
    expect(unload.defaultPrevented).toBe(true)
    await user.click(screen.getByRole('link', { name: '← Phiên bản biểu mẫu' }))
    await user.click(
      await screen.findByRole('button', { name: 'Tiếp tục chỉnh sửa' }),
    )
    expect(screen.getByLabelText('Nhãn', { exact: true })).toHaveValue(
      'Tên trường mẫu changed',
    )
    await user.click(screen.getByRole('link', { name: '← Phiên bản biểu mẫu' }))
    await user.click(
      await screen.findByRole('button', { name: 'Bỏ thay đổi và rời trang' }),
    )
    await screen.findByRole('button', { name: 'Tạo bản nháp' })
  })
  it('retains edits after a stale revision and failed reload, then explicitly loads the latest revision', async () => {
    seed()
    server.use(
      http.put('*/api/v1/admin/form-versions/10', () =>
        HttpResponse.json({ detail: 'Stale version.' }, { status: 409 }),
      ),
    )
    const user = userEvent.setup({ delay: null })
    app('/categories/forms/10')
    await user.type(
      await screen.findByLabelText('Nhãn', { exact: true }),
      ' local',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu bản nháp' }))
    await user.click(
      await screen.findByRole('button', { name: 'Tải bản mới nhất' }),
    )
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Hủy' }),
    )
    expect(screen.getByLabelText('Nhãn', { exact: true })).toHaveValue(
      'Tên trường mẫu local',
    )
    server.use(
      http.get('*/api/v1/admin/form-versions/10', () =>
        HttpResponse.json({ detail: 'Offline' }, { status: 503 }),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Tải bản mới nhất' }))
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Bỏ thay đổi và tải bản mới',
      }),
    )
    await screen.findByText(
      'Không tải được bản mới. Dữ liệu đang chỉnh sửa được giữ nguyên.',
    )
    expect(screen.getByLabelText('Nhãn', { exact: true })).toHaveValue(
      'Tên trường mẫu local',
    )
    server.use(
      http.get('*/api/v1/admin/form-versions/10', () =>
        HttpResponse.json({
          ...form,
          revision: 5,
          fields: form.fields.map((f) => ({ ...f, label: 'Bản máy chủ' })),
        }),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Tải bản mới nhất' }))
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Bỏ thay đổi và tải bản mới',
      }),
    )
    await screen.findByDisplayValue('Bản máy chủ')
    expect(screen.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled()
  }, 15000)
  it('archives a published form only after confirmation and keeps its fields read only', async () => {
    seed('PUBLISHED')
    let revision
    server.use(
      http.post(
        '*/api/v1/admin/form-versions/10/archive',
        async ({ request }) => {
          revision = (await request.json()).revision
          form = { ...form, status: 'ARCHIVED', revision: 5 }
          return HttpResponse.json(form)
        },
      ),
    )
    const user = userEvent.setup({ delay: null })
    app('/categories/forms/10')
    expect(await screen.findByLabelText('Mã trường')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Lưu trữ biểu mẫu' }))
    expect(revision).toBeUndefined()
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Xác nhận lưu trữ',
      }),
    )
    await screen.findByText('Đã lưu trữ biểu mẫu.')
    expect(revision).toBe(4)
    expect(screen.getByLabelText('Mã trường')).toBeDisabled()
    expect(
      screen.queryByRole('button', { name: 'Lưu trữ biểu mẫu' }),
    ).not.toBeInTheDocument()
  })
  it('maps publish errors to the field without changing the draft', async () => {
    seed()
    server.use(
      http.post('*/api/v1/admin/form-versions/10/publish', () =>
        HttpResponse.json(
          {
            fieldErrors: {
              'fields[0].validationRules.maxLength':
                'Maximum must be greater than or equal to minimum.',
            },
          },
          { status: 422 },
        ),
      ),
    )
    const user = userEvent.setup({ delay: null })
    app('/categories/forms/10')
    await screen.findByLabelText('Mã trường')
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Phát hành biểu mẫu' }),
      ).toBeEnabled(),
    )
    await user.click(screen.getByRole('button', { name: 'Phát hành biểu mẫu' }))
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Xác nhận phát hành',
      }),
    )
    await screen.findAllByText(
      'Giá trị tối đa phải lớn hơn hoặc bằng giá trị tối thiểu.',
      { exact: false },
    )
    expect(screen.getByLabelText('Nhãn', { exact: true })).toHaveValue(
      'Tên trường mẫu',
    )
  })
  it('creates and updates all category request fields without sending response metadata', async () => {
    let created, updated
    server.use(
      http.post('*/api/v1/admin/categories', async ({ request }) => {
        created = await request.json()
        return HttpResponse.json({ ...created, id: 2 }, { status: 201 })
      }),
      http.put('*/api/v1/admin/categories/1', async ({ request }) => {
        updated = await request.json()
        return HttpResponse.json({ ...updated, id: 1 })
      }),
    )
    const user = userEvent.setup({ delay: null })
    app()
    await user.click(
      await screen.findByRole('button', { name: 'Tạo danh mục' }),
    )
    await user.type(screen.getByLabelText('Mã', { exact: true }), 'FACILITY')
    await user.type(
      screen.getByLabelText('Tên', { exact: true }),
      'Cơ sở vật chất',
    )
    await user.type(
      screen.getByLabelText('Mô tả', { exact: true }),
      'Sửa chữa phòng học',
    )
    await user.selectOptions(
      screen.getByLabelText('Chính sách tài sản'),
      'REQUIRED',
    )
    await user.selectOptions(
      screen.getByLabelText('Mức ưu tiên mặc định'),
      'HIGH',
    )
    await user.click(screen.getByLabelText('Bắt buộc địa điểm'))
    await user.click(screen.getByLabelText('Danh mục đang hoạt động'))
    await user.click(screen.getByRole('button', { name: 'Lưu danh mục' }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(created).toEqual({
      code: 'FACILITY',
      name: 'Cơ sở vật chất',
      description: 'Sửa chữa phòng học',
      assetPolicy: 'REQUIRED',
      defaultPriority: 'HIGH',
      locationRequired: false,
      active: false,
    })
    await user.click(screen.getByRole('button', { name: 'Sửa IT support' }))
    expect(screen.getByLabelText('Mã', { exact: true })).toHaveAttribute(
      'readonly',
    )
    await user.clear(screen.getByLabelText('Tên', { exact: true }))
    await user.type(
      screen.getByLabelText('Tên', { exact: true }),
      'Hỗ trợ kỹ thuật',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu danh mục' }))
    await waitFor(() => expect(updated?.name).toBe('Hỗ trợ kỹ thuật'))
    expect(updated).not.toHaveProperty('id')
    expect(updated.code).toBe('IT')
  })
  it('preserves unsaved category edits until discard is confirmed', async () => {
    const user = userEvent.setup({ delay: null })
    app()
    await user.click(
      await screen.findByRole('button', { name: 'Tạo danh mục' }),
    )
    await user.type(screen.getByLabelText('Tên', { exact: true }), 'Chưa lưu')
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    await user.click(screen.getByRole('button', { name: 'Tiếp tục chỉnh sửa' }))
    expect(screen.getByLabelText('Tên', { exact: true })).toHaveValue(
      'Chưa lưu',
    )
    await user.click(screen.getByRole('button', { name: 'Hủy' }))
    await user.click(
      screen.getByRole('button', { name: 'Bỏ thay đổi', exact: true }),
    )
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
  })
  it('shows Vietnamese loading, retry and empty states', async () => {
    let resolve
    server.use(
      http.get(
        '*/api/v1/admin/categories',
        () =>
          new Promise((r) => {
            resolve = r
          }),
      ),
    )
    const user = userEvent.setup({ delay: null })
    app()
    expect(screen.getByText('Đang tải…')).toBeInTheDocument()
    await waitFor(() => expect(resolve).toBeTypeOf('function'))
    resolve(HttpResponse.json({}, { status: 503 }))
    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))
    await waitFor(() =>
      expect(screen.getByText('Đang tải…')).toBeInTheDocument(),
    )
    resolve(HttpResponse.json([]))
    await screen.findByText(
      'Chưa có danh mục. Tạo danh mục để thiết kế biểu mẫu đầu tiên.',
    )
  })

  it('creates a draft, edits and saves fields, confirms publication and restores saved data after remount', async () => {
    const user = userEvent.setup()
    const view = app()
    await user.click(
      await screen.findByRole('button', { name: 'Phiên bản biểu mẫu' }),
    )
    expect(
      await screen.findByText(
        'Chưa có phiên bản biểu mẫu. Hãy tạo bản nháp đầu tiên.',
      ),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tạo bản nháp' }))
    await screen.findByRole('heading', { name: 'IT support · Phiên bản 1' })
    await user.selectOptions(
      await screen.findByLabelText('Kỹ năng mặc định'),
      '2',
    )
    await user.selectOptions(
      screen.getByLabelText('Chính sách SLA mặc định'),
      '3',
    )
    await user.click(screen.getByRole('button', { name: 'Thêm trường' }))
    await user.click(screen.getByLabelText('Mã trường'))
    await user.paste('description')
    await user.click(screen.getByLabelText('Nhãn', { exact: true }))
    await user.paste('Describe the issue')
    await user.selectOptions(screen.getByLabelText('Kiểu trường'), 'TEXTAREA')
    expect(
      screen.getByRole('button', { name: 'Phát hành biểu mẫu' }),
    ).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Lưu bản nháp' }))
    expect(await screen.findByText('Đã lưu bản nháp.')).toBeInTheDocument()
    expect(savedPayload.fields[0]).toMatchObject({
      fieldKey: 'description',
      fieldType: 'TEXTAREA',
      displayOrder: 0,
      validationRules: {},
    })
    await user.click(screen.getByRole('button', { name: 'Phát hành biểu mẫu' }))
    const dialog = await screen.findByRole('dialog')
    expect(publishCount).toBe(0)
    await user.click(
      within(dialog).getByRole('button', { name: 'Xác nhận phát hành' }),
    )
    expect(await screen.findByText(/Biểu mẫu này chỉ đọc/)).toBeInTheDocument()
    expect(publishCount).toBe(1)
    expect(screen.getByLabelText('Mã trường')).toBeDisabled()
    view.unmount()
    app('/categories/forms/10')
    expect(await screen.findByDisplayValue('Describe the issue')).toBeDisabled()
    expect(
      screen.queryByRole('button', { name: 'Lưu bản nháp' }),
    ).not.toBeInTheDocument()
  }, 15000)

  it('shows backend field errors and preserves edits after a failed save', async () => {
    form = {
      id: 10,
      category,
      versionNumber: 1,
      revision: 1,
      status: 'DRAFT',
      requiresEquipment: false,
      minAttachments: 0,
      defaultSkillId: 2,
      defaultSlaPolicyId: 3,
      fields: [
        {
          fieldKey: 'name',
          label: 'Tên',
          fieldType: 'TEXT',
          active: true,
          required: false,
          validationRules: {},
          options: [],
        },
      ],
    }
    server.use(
      http.put('*/api/v1/admin/form-versions/10', () =>
        HttpResponse.json(
          {
            detail: 'Invalid form.',
            fieldErrors: { 'fields[0].label': 'Backend rejected this label.' },
          },
          { status: 422 },
        ),
      ),
    )
    const user = userEvent.setup()
    app('/categories/forms/10')
    await user.type(
      await screen.findByLabelText('Nhãn', { exact: true }),
      ' changed',
    )
    await user.click(screen.getByRole('button', { name: 'Lưu bản nháp' }))
    await waitFor(() =>
      expect(
        screen.getAllByText('Backend rejected this label.').length,
      ).toBeGreaterThan(0),
    )
    expect(screen.getByLabelText('Nhãn', { exact: true })).toHaveValue(
      'Tên changed',
    )
  })

  it.each(['REQUESTER', 'MANAGER', 'TECHNICIAN'])(
    'guards administration for %s',
    async (role) => {
      app('/categories', [role])
      expect(
        await screen.findByRole('heading', {
          name: 'Bạn không có quyền truy cập trang này',
        }),
      ).toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Tạo danh mục' }),
      ).not.toBeInTheDocument()
    },
  )
})
