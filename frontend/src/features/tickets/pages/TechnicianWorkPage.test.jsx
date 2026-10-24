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
const BrowserURL = globalThis.URL
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
  vi.stubGlobal(
    'URL',
    class extends BrowserURL {
      static createObjectURL = vi.fn(() => `blob:work-${Math.random()}`)
      static revokeObjectURL = vi.fn()
    },
  )
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

  it.each([false, true])(
    'uploads optional result images and refreshes attachments (mock flag=%s)',
    async (mockFlag) => {
      vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', String(mockFlag))
      actor = {
        id: 24,
        fullName: 'Phạm Dũng',
        status: 'ACTIVE',
        roles: ['TECHNICIAN'],
      }
      mount('/tickets/13', actor)
      expect(
        await screen.findByRole('button', { name: 'Thêm nhật ký' }),
      ).toBeInTheDocument()
      expect(screen.getByText(/Ảnh không bắt buộc/)).toBeInTheDocument()
      const first = new File(['image'], 'first.png', { type: 'image/png' })
      const second = new File(['image'], 'second.jpg', { type: 'image/jpeg' })
      await userEvent.upload(screen.getByLabelText('Chọn ảnh'), [first, second])
      expect(screen.getAllByRole('img', { name: /Xem trước/ })).toHaveLength(2)
      await userEvent.click(
        screen.getAllByRole('button', { name: 'Bỏ ảnh' })[0],
      )
      expect(
        screen.queryByRole('img', { name: /first.png/ }),
      ).not.toBeInTheDocument()
      const ticket = mock.store.tickets.find((item) => item.id === 13)
      const version = ticket.version
      let finish
      const upload = vi.spyOn(ticketApi, 'uploadWorkResult').mockImplementation(
        (id, files) =>
          new Promise((resolve) => {
            finish = () => {
              const created = files.map((file, index) => ({
                id: 1000 + index,
                fieldDefinitionId: null,
                attachmentType: 'WORK_RESULT',
                originalName: file.name,
                contentType: file.type,
                sizeBytes: file.size,
                uploadedBy: actor,
                createdAt: new Date().toISOString(),
              }))
              ticket.attachments.push(...created)
              resolve(created)
            }
          }),
      )
      await userEvent.click(
        screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
      )
      expect(
        screen.getByRole('button', { name: 'Đang tải ảnh...' }),
      ).toBeDisabled()
      expect(screen.getByLabelText('Chọn ảnh')).toBeDisabled()
      fireEvent.click(screen.getByRole('button', { name: 'Đang tải ảnh...' }))
      expect(upload).toHaveBeenCalledExactlyOnceWith(13, [second])
      await act(async () => finish())
      await screen.findByText('Đã lưu ảnh kết quả.')
      expect(
        screen.queryByRole('img', { name: /Xem trước/ }),
      ).not.toBeInTheDocument()
      expect(
        await screen.findByRole('button', { name: 'Xem ảnh second.jpg' }),
      ).toBeEnabled()
      expect(ticket.version).toBe(version)
      expect(ticket.status).toBe('IN_PROGRESS')
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
    },
  )

  it('keeps selected files after failure and retries only when submitted again', async () => {
    actor = { id: 24, status: 'ACTIVE', roles: ['TECHNICIAN'] }
    mount('/tickets/13', actor)
    const input = await screen.findByLabelText('Chọn ảnh')
    const file = new File(['image'], 'retry.png', { type: 'image/png' })
    const upload = vi
      .spyOn(ticketApi, 'uploadWorkResult')
      .mockRejectedValueOnce({
        response: { status: 500, data: { detail: 'Không thể lưu ảnh.' } },
      })
      .mockResolvedValueOnce([])
    await userEvent.upload(input, file)
    await userEvent.click(
      screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
    )
    await screen.findByText('Không thể lưu ảnh.')
    expect(
      screen.getByRole('img', { name: 'Xem trước retry.png' }),
    ).toBeInTheDocument()
    expect(upload).toHaveBeenCalledTimes(1)
    await userEvent.click(
      screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
    )
    await screen.findByText('Đã lưu ảnh kết quả.')
    expect(upload).toHaveBeenNthCalledWith(2, 13, [file])
    expect(
      screen.queryByRole('img', { name: /Xem trước/ }),
    ).not.toBeInTheDocument()
  })

  it.each([403, 404, 422])(
    'keeps the draft and requires reload after upload error %s',
    async (status) => {
      actor = { id: 24, status: 'ACTIVE', roles: ['TECHNICIAN'] }
      mount('/tickets/13', actor)
      const input = await screen.findByLabelText('Chọn ảnh')
      const upload = vi
        .spyOn(ticketApi, 'uploadWorkResult')
        .mockImplementation(async () => {
          mock.store.tickets.find(
            (ticket) => ticket.id === 13,
          ).currentAssignment = null
          throw { response: { status } }
        })
      await userEvent.upload(
        input,
        new File(['image'], 'keep.png', { type: 'image/png' }),
      )
      await userEvent.click(
        screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
      )
      await screen.findByText(/Không còn quyền xử lý/)
      expect(
        screen.getByRole('img', { name: 'Xem trước keep.png' }),
      ).toBeInTheDocument()
      expect(
        screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
      ).toBeDisabled()
      expect(upload).toHaveBeenCalledTimes(1)
      await userEvent.click(
        screen.getByRole('button', { name: 'Tải lại phiếu' }),
      )
      await waitFor(() =>
        expect(screen.queryByLabelText('Chọn ảnh')).not.toBeInTheDocument(),
      )
    },
  )

  it.each([
    [
      'empty.png',
      'image/png',
      0,
      'Mỗi ảnh phải có dung lượng từ 1 byte đến 5 MiB.',
    ],
    [
      'large.png',
      'image/png',
      5 * 1024 * 1024 + 1,
      'Mỗi ảnh phải có dung lượng từ 1 byte đến 5 MiB.',
    ],
    ['bad.gif', 'image/gif', 1, 'Chỉ chấp nhận ảnh JPEG hoặc PNG.'],
  ])('validates %s before uploading', async (name, type, size, error) => {
    actor = { id: 24, status: 'ACTIVE', roles: ['TECHNICIAN'] }
    const upload = vi.spyOn(ticketApi, 'uploadWorkResult')
    mount('/tickets/13', actor)
    const input = await screen.findByLabelText('Chọn ảnh')
    fireEvent.change(input, {
      target: { files: [new File([new Uint8Array(size)], name, { type })] },
    })
    expect(screen.getByText(error)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
    ).toBeDisabled()
    expect(upload).not.toHaveBeenCalled()
  })

  it('limits selections to five images and allows removing an extra image', async () => {
    actor = { id: 24, status: 'ACTIVE', roles: ['TECHNICIAN'] }
    mount('/tickets/13', actor)
    await userEvent.upload(
      await screen.findByLabelText('Chọn ảnh'),
      Array.from(
        { length: 6 },
        (_, i) => new File(['x'], `${i}.png`, { type: 'image/png' }),
      ),
    )
    expect(screen.getByText('Chọn từ 1 đến 5 ảnh.')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
    ).toBeDisabled()
    await userEvent.click(screen.getAllByRole('button', { name: 'Bỏ ảnh' })[0])
    expect(
      screen.getByRole('button', { name: 'Tải ảnh kết quả' }),
    ).toBeEnabled()
  })

  it.each([['REQUESTER'], ['MANAGER'], ['ADMIN'], ['TECHNICIAN']])(
    'hides upload for a visible non-assignee with roles %j',
    async (...roles) => {
      actor = { id: 21, status: 'ACTIVE', roles }
      const ticket = mock.store.tickets.find((item) => item.id === 13)
      vi.spyOn(ticketApi, 'getById').mockResolvedValue(ticket)
      vi.spyOn(ticketApi, 'workLogs').mockResolvedValue([])
      mount('/tickets/13', actor)
      await screen.findByRole('heading', { name: 'NX-2026-000013' })
      expect(screen.queryByLabelText('Chọn ảnh')).not.toBeInTheDocument()
    },
  )

  it.each(['ASSIGNED', 'RESOLVED', 'CLOSED'])(
    'hides upload outside IN_PROGRESS (%s)',
    async (status) => {
      actor = { id: 24, status: 'ACTIVE', roles: ['TECHNICIAN'] }
      mock.store.tickets.find((item) => item.id === 13).status = status
      mount('/tickets/13', actor)
      await screen.findByRole('heading', { name: 'NX-2026-000013' })
      expect(screen.queryByLabelText('Chọn ảnh')).not.toBeInTheDocument()
    },
  )

  it('allows my-work, start, work-logs and resolve without mock gate', async () => {
    vi.stubEnv('DEV', false)
    vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
    mount('/my-work')
    expect(
      await screen.findByRole('link', { name: 'NX-2026-000012' }),
    ).toBeInTheDocument()
    mount('/tickets/12')
    expect(
      await screen.findByRole('button', {
        name: 'Bắt đầu xử lý',
        exact: true,
      }),
    ).toBeEnabled()
  })
})
