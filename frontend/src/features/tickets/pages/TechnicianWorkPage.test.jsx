import {
  act,
  cleanup,
  fireEvent,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import { createTicketWorkflowMock } from '../mocks/ticketWorkflowHandlers'
import { ticketApi } from '../api/ticketApi'
import { renderTicketApp } from '../testsupport/renderTicketApp'

const technician = {
  id: 21,
  fullName: 'Nguyễn An',
  status: 'ACTIVE',
  roles: ['TECHNICIAN'],
}
let actor = technician
const mock = createTicketWorkflowMock({ getActor: () => actor })
const server = setupServer(...mock.handlers)
const clients = []
const mount = (entry, user = technician) => {
  const result = renderTicketApp(entry, user)
  clients.push(result.client)
  return result
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  mock.reset()
  actor = technician
  vi.stubEnv('DEV', true)
  vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'true')
})
afterEach(() => {
  cleanup()
  clients.splice(0).forEach((client) => client.clear())
  server.resetHandlers()
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})
afterAll(() => server.close())

describe('technician work UI', () => {
  it('shows current assignments only, with list navigation and filters', async () => {
    mount('/my-work')
    expect(
      await screen.findByRole('link', { name: 'NX-2026-000012' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('link', { name: 'NX-2026-000015' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText('1 phiếu')).toBeInTheDocument()
    await userEvent.selectOptions(
      screen.getByLabelText('Trạng thái'),
      'IN_PROGRESS',
    )
    expect(
      await screen.findByText('Không có công việc phù hợp.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('URL hiện tại')).toHaveTextContent(
      'status=IN_PROGRESS',
    )
    await userEvent.selectOptions(
      screen.getByLabelText('Trạng thái'),
      'ASSIGNED',
    )
    await userEvent.click(
      await screen.findByRole('link', { name: 'NX-2026-000012' }),
    )
    expect(
      await screen.findByRole('link', { name: /Công việc của tôi/ }),
    ).toHaveAttribute('href', expect.stringContaining('/my-work?'))
  })

  it('starts, adds a valid log, then resolves and removes the current assignment', async () => {
    const { client } = mount('/tickets/12')
    const invalidation = vi.spyOn(client, 'invalidateQueries')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bắt đầu xử lý', exact: true }),
    )
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Xác nhận bắt đầu xử lý',
      }),
    )
    await screen.findByRole('button', { name: 'Thêm nhật ký' })
    expect(invalidation).toHaveBeenCalledWith({ queryKey: ['dashboard'] })
    expect(
      screen.getByRole('button', { name: 'Hoàn tất xử lý' }),
    ).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Thêm nhật ký' }))
    const dialog = within(screen.getByRole('dialog'))
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận thêm nhật ký' }),
    )
    expect(dialog.getByText('Nhập nội dung công việc.')).toBeInTheDocument()
    await userEvent.type(dialog.getByLabelText('Nội dung'), 'Đã sửa thiết bị')
    fireEvent.change(dialog.getByLabelText('Bắt đầu'), {
      target: { value: '2026-10-10T09:00' },
    })
    fireEvent.change(dialog.getByLabelText('Kết thúc (không bắt buộc)'), {
      target: { value: '2026-10-10T09:20' },
    })
    await userEvent.type(
      dialog.getByLabelText('Số phút (không bắt buộc)'),
      '20',
    )
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận thêm nhật ký' }),
    )
    expect(await screen.findByText('Đã sửa thiết bị')).toBeInTheDocument()
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Hoàn tất xử lý' }),
      ).toBeEnabled(),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Hoàn tất xử lý' }),
    )
    const resolve = within(screen.getByRole('dialog'))
    await userEvent.click(
      resolve.getByRole('button', { name: 'Xác nhận hoàn tất xử lý' }),
    )
    expect(resolve.getByText('Nhập tóm tắt kết quả.')).toBeInTheDocument()
    await userEvent.type(
      resolve.getByLabelText('Tóm tắt kết quả'),
      'Hoạt động bình thường',
    )
    await userEvent.click(
      resolve.getByRole('button', { name: 'Xác nhận hoàn tất xử lý' }),
    )
    await screen.findByText(/Phân công hiện tại: Không còn phân công hiện tại/)
    expect(mock.store.tickets.find((item) => item.id === 12)).toMatchObject({
      status: 'RESOLVED',
      currentAssignment: null,
    })
  })

  it('requires reload after a stale version', async () => {
    mount('/tickets/12')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bắt đầu xử lý', exact: true }),
    )
    mock.failNextAction(409)
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Xác nhận bắt đầu xử lý',
      }),
    )
    const dialog = within(screen.getByRole('dialog'))
    expect(await dialog.findByText(/Phiếu đã thay đổi/)).toBeInTheDocument()
    expect(
      dialog.getByRole('button', { name: 'Xác nhận bắt đầu xử lý' }),
    ).toBeDisabled()
    await userEvent.click(dialog.getByRole('button', { name: 'Tải lại phiếu' }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(
      screen.getByRole('button', { name: 'Bắt đầu xử lý', exact: true }),
    ).toBeEnabled()
  })

  it('removes controls after assignment loss and reload', async () => {
    mount('/tickets/12')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bắt đầu xử lý', exact: true }),
    )
    mock.failNextAction(403)
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Xác nhận bắt đầu xử lý',
      }),
    )
    await userEvent.click(
      await within(screen.getByRole('dialog')).findByRole('button', {
        name: 'Tải lại phiếu',
      }),
    )
    await screen.findByText(/Phân công hiện tại: Không còn phân công hiện tại/)
    expect(
      screen.getByRole('button', { name: 'Bắt đầu xử lý', exact: true }),
    ).toBeDisabled()
  })

  it('validates image selection and retains it after an upload error for retry', async () => {
    const createUrl = vi.fn(() => 'blob:preview')
    const revokeUrl = vi.fn()
    let finishUpload
    const pendingUpload = new Promise((resolve) => {
      finishUpload = resolve
    })
    vi.stubGlobal(
      'URL',
      Object.assign(class extends URL {}, {
        createObjectURL: createUrl,
        revokeObjectURL: revokeUrl,
      }),
    )
    const upload = vi
      .spyOn(ticketApi, 'uploadWorkResult')
      .mockRejectedValueOnce({
        response: { status: 500, data: { detail: 'Lỗi upload demo' } },
      })
      .mockImplementationOnce(() => pendingUpload)
    actor = {
      id: 24,
      fullName: 'Phạm Dũng',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    mount('/tickets/13', actor)
    const picker = await screen.findByLabelText('Chọn ảnh')
    fireEvent.change(picker, {
      target: { files: [new File(['bad'], 'bad.gif', { type: 'image/gif' })] },
    })
    expect(screen.getByText(/Chỉ chấp nhận ảnh JPEG/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ ảnh' }))
    fireEvent.change(picker, {
      target: {
        files: [new File(['image'], 'result.png', { type: 'image/png' })],
      },
    })
    expect(screen.getByAltText('Xem trước result.png')).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: 'Tải ảnh mô phỏng' }),
    )
    expect(await screen.findByText('Lỗi upload demo')).toBeInTheDocument()
    expect(screen.getByAltText('Xem trước result.png')).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: 'Tải ảnh mô phỏng' }),
    )
    expect(
      screen.getByRole('button', { name: 'Đang tải ảnh...' }),
    ).toBeDisabled()
    await act(async () =>
      finishUpload([{ id: 1, attachmentType: 'WORK_RESULT' }]),
    )
    await screen.findByText(/Ảnh đã tải trong bản mô phỏng/)
    expect(upload).toHaveBeenCalledTimes(2)
    expect(
      screen.queryByAltText('Xem trước result.png'),
    ).not.toBeInTheDocument()
    expect(revokeUrl).toHaveBeenCalledWith('blob:preview')
  })

  it('requires reload before retrying an image upload after a 409', async () => {
    vi.stubGlobal(
      'URL',
      Object.assign(class extends URL {}, {
        createObjectURL: () => 'blob:preview',
        revokeObjectURL: vi.fn(),
      }),
    )
    const upload = vi
      .spyOn(ticketApi, 'uploadWorkResult')
      .mockRejectedValueOnce({ response: { status: 409 } })
      .mockResolvedValueOnce([{ id: 1, attachmentType: 'WORK_RESULT' }])
    actor = {
      id: 24,
      fullName: 'Phạm Dũng',
      status: 'ACTIVE',
      roles: ['TECHNICIAN'],
    }
    mount('/tickets/13', actor)
    fireEvent.change(await screen.findByLabelText('Chọn ảnh'), {
      target: {
        files: [new File(['image'], 'result.png', { type: 'image/png' })],
      },
    })
    await userEvent.click(
      screen.getByRole('button', { name: 'Tải ảnh mô phỏng' }),
    )
    expect(await screen.findByText(/Phiếu đã thay đổi/)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Tải ảnh mô phỏng' }),
    ).toBeDisabled()
    expect(screen.getByLabelText('Chọn ảnh')).toBeDisabled()
    expect(upload).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('button', { name: 'Tải lại phiếu' }))
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Tải ảnh mô phỏng' }),
      ).toBeEnabled(),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Tải ảnh mô phỏng' }),
    )
    expect(
      await screen.findByText(/Ảnh đã tải trong bản mô phỏng/),
    ).toBeInTheDocument()
    expect(upload).toHaveBeenCalledTimes(2)
  })

  it('keeps missing technician APIs unavailable outside development mock mode', async () => {
    vi.stubEnv('DEV', false)
    mount('/my-work')
    expect(
      screen.getByText(/Danh sách công việc chưa khả dụng/),
    ).toBeInTheDocument()
    mount('/tickets/12')
    expect(
      await screen.findByText(/Chức năng công việc chưa khả dụng/),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Bắt đầu xử lý', exact: true }),
    ).toBeDisabled()
  })
})
