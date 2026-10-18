import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { CreateTicketPage } from './CreateTicketPage'
import { createFormFixture } from '../mocks/createTicketFixture'

beforeEach(() => {
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  URL.createObjectURL = vi.fn(() => 'blob:photo')
  URL.revokeObjectURL = vi.fn()
})
afterEach(() => vi.restoreAllMocks())
function open(overrides = {}) {
  const api = {
    categories: vi.fn(async () => [
      createFormFixture.category,
      { ...createFormFixture.category, id: 2, name: 'Other' },
    ]),
    form: vi.fn(async () => ({ ...createFormFixture, minAttachments: 0 })),
    locations: vi.fn(async () => [
      { id: 1, name: 'Room', code: 'A1', type: 'ROOM', active: true },
      { id: 2, name: 'Room2', code: 'A2', type: 'ROOM', active: true },
    ]),
    assets: vi.fn(async () => ({
      content: [{ id: 1, assetCode: 'AC-1' }],
      totalPages: 1,
    })),
    submit: vi.fn(async () => ({ id: 12, ticketNumber: 'NX-12' })),
    ...overrides,
  }
  const router = createMemoryRouter(
    [
      { path: '/tickets/new', element: <CreateTicketPage api={api} /> },
      { path: '/tickets/:id', element: <h1>Created detail</h1> },
      { path: '/tickets', element: <h1>List</h1> },
    ],
    { initialEntries: ['/tickets/new'] },
  )
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  return { api, router, user: userEvent.setup() }
}
async function fill(user) {
  await screen.findByRole('option', { name: 'Thiết bị phòng học' })
  await user.selectOptions(await screen.findByLabelText('Danh mục *'), '1')
  await screen.findByText(/Biểu mẫu phiên bản/)
  await user.type(screen.getByLabelText('Tiêu đề *'), 'Broken')
  await user.type(screen.getByLabelText('Mô tả *'), 'Details')
  await user.click(await screen.findByRole('button', { name: 'Chọn Room' }))
}
it('renders all nine fields and submits once while pending then routes to detail', async () => {
  let resolve
  const { api, user, router } = open({
    submit: vi.fn(
      () =>
        new Promise((r) => {
          resolve = r
        }),
    ),
  })
  await fill(user)
  expect(screen.getByLabelText('Số lượng')).toBeInTheDocument()
  expect(screen.getByLabelText('Ảnh thiết bị')).toHaveAttribute('type', 'file')
  await user.type(screen.getByLabelText('Số lượng'), '0')
  await user.selectOptions(screen.getByLabelText('Có thể sử dụng'), 'false')
  await user.dblClick(screen.getByRole('button', { name: 'Gửi phiếu' }))
  expect(api.submit).toHaveBeenCalledTimes(1)
  expect(screen.getByRole('button', { name: 'Đang gửi...' })).toBeDisabled()
  resolve({ id: 12, ticketNumber: 'NX-12' })
  await screen.findByText('Created detail')
  expect(router.state.location.state.createdTicketNumber).toBe('NX-12')
})
it.each([400, 401, 403, 404, 413, 415, 422, 500])(
  'retains input and shows errors for HTTP %s',
  async (status) => {
    const { user } = open({
      submit: vi.fn(async () => {
        throw {
          response: {
            status,
            data: { fieldErrors: { title: 'Server title error' } },
          },
        }
      }),
    })
    await fill(user)
    await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
    await screen.findAllByText(/Server title error/)
    expect(screen.getByLabelText('Tiêu đề *')).toHaveValue('Broken')
  },
)
it('requires confirmation before category and location reset', async () => {
  const { user } = open()
  await fill(user)
  await screen.findByRole('option', { name: /AC-1/ })
  await user.selectOptions(screen.getByLabelText('Tài sản hợp lệ'), '1')
  await user.click(screen.getByRole('button', { name: 'Chọn Room2' }))
  expect(
    screen.getByText('Đổi địa điểm sẽ xóa tài sản đã chọn.'),
  ).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(screen.getByLabelText('Tài sản hợp lệ')).toHaveValue('')
  await user.selectOptions(screen.getByLabelText('Danh mục *'), '2')
  await user.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(screen.getByLabelText('Tiêu đề *')).toHaveValue('Broken')
  await screen.findByText('Đã chọn: Chưa chọn')
})
it('reloads changed form on 409 while keeping compatible values', async () => {
  const { user, api } = open({
    submit: vi.fn(async () => {
      throw { response: { status: 409 } }
    }),
  })
  await fill(user)
  await user.type(screen.getByLabelText('Số lượng'), '0')
  await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
  await user.click(
    await screen.findByRole('button', { name: 'Tải lại biểu mẫu' }),
  )
  await waitFor(() => expect(api.form).toHaveBeenCalledTimes(2))
  expect(screen.getByLabelText('Số lượng')).toHaveValue(0)
  expect(screen.getByRole('button', { name: 'Gửi phiếu' })).toBeEnabled()
})
it('previews, removes and revokes evidence URLs', async () => {
  const { user } = open()
  await fill(user)
  await user.upload(
    screen.getByLabelText('Ảnh thiết bị'),
    new File(['x'], 'photo.png', { type: 'image/png' }),
  )
  expect(
    await screen.findByRole('img', { name: 'photo.png' }),
  ).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Xóa ảnh photo.png' }))
  expect(
    screen.queryByRole('img', { name: 'photo.png' }),
  ).not.toBeInTheDocument()
  expect(URL.revokeObjectURL).toHaveBeenCalled()
})

it('warns before leaving unsent input and allows staying or discarding', async () => {
  const { user, router } = open()
  await fill(user)
  await user.click(screen.getByRole('button', { name: 'Hủy / Quay lại' }))
  await screen.findByText(
    'Bạn có dữ liệu chưa gửi. Rời trang sẽ mất dữ liệu này.',
  )
  await user.click(screen.getByRole('button', { name: 'Tiếp tục nhập' }))
  expect(screen.getByLabelText('Tiêu đề *')).toHaveValue('Broken')
  await user.click(screen.getByRole('button', { name: 'Hủy / Quay lại' }))
  await user.click(screen.getByRole('button', { name: 'Xác nhận' }))
  await screen.findByRole('heading', { name: 'List' })
  expect(router.state.location.pathname).toBe('/tickets')
})
it('preserves input after a network failure', async () => {
  const { user, api } = open({
    submit: vi.fn(async () => {
      throw new Error('Offline')
    }),
  })
  await fill(user)
  await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
  await screen.findByText(/Không gửi được phiếu/)
  expect(
    screen.getByText(/Kiểm tra danh sách phiếu trước khi gửi lại/),
  ).toBeInTheDocument()
  expect(api.submit).toHaveBeenCalledTimes(1)
  expect(screen.getByLabelText('Tiêu đề *')).toHaveValue('Broken')
  expect(screen.getByRole('button', { name: 'Gửi phiếu' })).toBeEnabled()
})
it.each([413, 415])('keeps selected evidence after HTTP %s', async (status) => {
  const { user, api } = open({
    submit: vi.fn(async () => {
      throw { response: { status } }
    }),
  })
  await fill(user)
  await user.upload(
    screen.getByLabelText('Ảnh thiết bị'),
    new File(['sample'], 'keep.png', { type: 'image/png' }),
  )
  await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
  await waitFor(() => expect(api.submit).toHaveBeenCalledTimes(1))
  expect(screen.getByRole('img', { name: 'keep.png' })).toBeInTheDocument()
  expect(screen.getByLabelText('Tiêu đề *')).toHaveValue('Broken')
})
it('requires a location when locationRequired is false in the published category', async () => {
  const { user, api } = open({
    form: vi.fn(async () => ({
      ...createFormFixture,
      minAttachments: 0,
      category: { ...createFormFixture.category, locationRequired: false },
    })),
  })
  await screen.findByRole('option', { name: 'Thiết bị phòng học' })
  await user.selectOptions(screen.getByLabelText('Danh mục *'), '1')
  await screen.findByText(/Địa điểm luôn bắt buộc/)
  await user.type(screen.getByLabelText('Tiêu đề *'), 'Broken')
  await user.type(screen.getByLabelText('Mô tả *'), 'Details')
  await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
  await screen.findAllByText(/Chọn địa điểm\./)
  expect(api.submit).not.toHaveBeenCalled()
})
it('does not render a draft or allow submission', async () => {
  const { user, api } = open({
    form: vi.fn(async () => ({ ...createFormFixture, status: 'DRAFT' })),
  })
  await screen.findByRole('option', { name: 'Thiết bị phòng học' })
  await user.selectOptions(screen.getByLabelText('Danh mục *'), '1')
  await screen.findByText('Danh mục chưa có biểu mẫu đã phát hành hợp lệ.')
  expect(screen.queryByLabelText('Số lượng')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Gửi phiếu' })).toBeDisabled()
  expect(api.submit).not.toHaveBeenCalled()
})
it('hides asset input for forbidden categories', async () => {
  const { user, api } = open({
    form: vi.fn(async () => ({
      ...createFormFixture,
      category: { ...createFormFixture.category, assetPolicy: 'FORBIDDEN' },
    })),
  })
  await fill(user)
  expect(screen.queryByLabelText('Tài sản hợp lệ')).not.toBeInTheDocument()
  expect(api.assets).not.toHaveBeenCalled()
})
it('rejects excessive image count and size before adding evidence', async () => {
  const { user } = open()
  await fill(user)
  const picker = screen.getByLabelText('Ảnh minh chứng chung')
  await user.upload(
    picker,
    Array.from(
      { length: 6 },
      (_, i) => new File(['x'], `${i}.png`, { type: 'image/png' }),
    ),
  )
  await screen.findByText('Vượt giới hạn số ảnh cho phép.')
  expect(screen.queryAllByRole('img')).toHaveLength(0)
  await user.upload(
    picker,
    new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', {
      type: 'image/png',
    }),
  )
  await screen.findByText('Ảnh vượt giới hạn dung lượng cho phép.')
  expect(screen.queryAllByRole('img')).toHaveLength(0)
})
