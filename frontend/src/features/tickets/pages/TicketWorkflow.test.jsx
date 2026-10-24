import {
  cleanup,
  fireEvent,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
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
import { ticketApi } from '../api/ticketApi'
import { createTicketWorkflowMock } from '../mocks/ticketWorkflowHandlers'
import { WORKFLOW_MOCK_MANAGER } from '../mocks/ticketWorkflowFixtures'
import { renderTicketApp } from '../testsupport/renderTicketApp'
import { demoScope } from '../../../shared/config/demoScope'

// Preserve the deferred components' tests without enabling them in the demo.
vi.mock('../../../shared/config/demoScope', async (importOriginal) => {
  const { demoScope } = await importOriginal()
  return { demoScope: { ...demoScope, actions: { ...demoScope.actions } } }
})

const mock = createTicketWorkflowMock()
const server = setupServer(...mock.handlers)
const clients = []
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  for (const action of ['reject', 'cancel', 'reassign', 'unassign', 'reopen'])
    demoScope.actions[action] = true
  mock.reset()
  vi.stubEnv('DEV', true)
  vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'true')
})
afterEach(() => {
  cleanup()
  clients.splice(0).forEach((client) => client.clear())
  server.resetHandlers()
  vi.restoreAllMocks()
  vi.unstubAllEnvs()
})
afterAll(() => server.close())

async function mount(id = 10, user = WORKFLOW_MOCK_MANAGER) {
  const result = renderTicketApp(`/tickets/${id}`, user)
  clients.push(result.client)
  await screen.findByText(`NX-2026-${String(id).padStart(6, '0')}`)
  return result
}
async function openDialog(label) {
  await userEvent.click(
    screen.getByRole('button', { name: label, exact: true }),
  )
  return within(await screen.findByRole('dialog'))
}
async function waitForSuccess(label) {
  await screen.findByText(`${label} thành công.`)
  await waitFor(() =>
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
  )
}

describe('manager ticket workflow UI', () => {
  it.each([false, true])(
    'reviews and assigns with the workflow mock disabled (production=%s)',
    async (production) => {
      for (const action of [
        'reject',
        'cancel',
        'reassign',
        'unassign',
        'reopen',
      ])
        demoScope.actions[action] = false
      if (production) vi.stubEnv('DEV', false)
      else vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
      const review = vi.spyOn(ticketApi, 'review')
      const assign = vi.spyOn(ticketApi, 'assign')
      await mount()
      expect(
        screen.queryByRole('button', { name: 'Từ chối', exact: true }),
      ).not.toBeInTheDocument()
      const confirmation = await openDialog('Bắt đầu xem xét')
      await userEvent.click(
        confirmation.getByRole('button', { name: 'Xác nhận bắt đầu xem xét' }),
      )
      await screen.findByText('Bắt đầu xem xét thành công.')
      expect(review).toHaveBeenCalledExactlyOnceWith(10, { version: 0 })
      const dialog = await openDialog('Phân công')
      expect(dialog.queryByLabelText(/Lý do ghi đè/)).not.toBeInTheDocument()
      await userEvent.click(
        await dialog.findByRole('radio', { name: 'Chọn Nguyễn An' }),
      )
      await userEvent.click(
        dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
      )
      await screen.findByText('Phân công thành công.')
      expect(assign).toHaveBeenCalledExactlyOnceWith(10, {
        version: 1,
        technicianId: 21,
      })
      expect(
        screen.queryByRole('button', { name: 'Phân công lại', exact: true }),
      ).not.toBeInTheDocument()
      expect(
        screen.getByRole('heading', { name: 'Lịch sử trạng thái' }),
      ).toBeInTheDocument()
      expect(screen.getByText('Chi tiết phiếu')).toBeInTheDocument()
      expect(
        screen.queryByText(/thành công trong bản mô phỏng/),
      ).not.toBeInTheDocument()
    },
  )
  it('distinguishes unavailable assignment data from an unassigned ticket', async () => {
    const { currentAssignment: _assignment, ...detail } = mock.store.tickets[0]
    server.use(http.get('*/api/v1/tickets/10', () => HttpResponse.json(detail)))
    vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
    await mount()
    expect(
      screen.getByText(/Dữ liệu phân công chưa được hỗ trợ/),
    ).toBeInTheDocument()
    expect(screen.queryByText(/Chưa phân công/)).not.toBeInTheDocument()
  })
  it.each([['REQUESTER'], ['TECHNICIAN'], ['REQUESTER', 'TECHNICIAN']])(
    'hides manager controls for roles %j',
    async (...roles) => {
      await mount(10, { id: 4, roles })
      if (roles.includes('REQUESTER'))
        expect(
          screen.getByRole('button', { name: 'Hủy phiếu', exact: true }),
        ).toBeEnabled()
      else
        expect(
          screen.queryByRole('heading', { name: 'Xử lý phiếu' }),
        ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'Bắt đầu xem xét' }),
      ).not.toBeInTheDocument()
      expect(screen.getByText('Mô phỏng workflow')).toBeInTheDocument()
    },
  )
  it('reviews then assigns with exact version payloads and refreshes detail/list/history', async () => {
    const review = vi.spyOn(ticketApi, 'review')
    const assign = vi.spyOn(ticketApi, 'assign')
    const { client } = await mount()
    const invalidation = vi.spyOn(client, 'invalidateQueries')
    const dialog = await openDialog('Bắt đầu xem xét')
    expect(dialog.queryByLabelText(/^Lý do/)).not.toBeInTheDocument()
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận bắt đầu xem xét' }),
    )
    await waitForSuccess('Bắt đầu xem xét')
    expect(review).toHaveBeenCalledExactlyOnceWith(10, { version: 0 })
    expect(invalidation).toHaveBeenCalledWith({ queryKey: ['dashboard'] })
    expect(
      screen.queryByRole('button', { name: 'Bắt đầu xem xét', exact: true }),
    ).not.toBeInTheDocument()
    const assignment = await openDialog('Phân công')
    await userEvent.click(
      await assignment.findByRole('radio', { name: 'Chọn Nguyễn An' }),
    )
    await userEvent.click(
      assignment.getByRole('button', {
        name: 'Xác nhận phân công',
        exact: true,
      }),
    )
    await waitForSuccess('Phân công')
    expect(assign).toHaveBeenCalledExactlyOnceWith(10, {
      version: 1,
      technicianId: 21,
    })
    expect(
      screen.getByText(/Kỹ thuật viên hiện tại: Nguyễn An/),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Phân công lại', exact: true }),
    ).toBeEnabled()
    const history = screen
      .getByRole('heading', { name: 'Lịch sử trạng thái' })
      .closest('.card')
    expect(within(history).getByText('Bắt đầu xem xét')).toBeInTheDocument()
    expect(within(history).getByText('Phân công')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: /Danh sách phiếu/ }))
    const number = await screen.findByRole('link', { name: /NX-2026-000010/ })
    expect(
      within(number.closest('tr')).getByText('Đã phân công'),
    ).toBeInTheDocument()
  })
  it.each([
    ['Từ chối', 'reject'],
    ['Hủy phiếu', 'cancel'],
  ])(
    'requires a reason, preserves it on 400, and confirms %s',
    async (label, action) => {
      const request = vi.spyOn(ticketApi, action)
      await mount()
      const dialog = await openDialog(label)
      await userEvent.click(
        dialog.getByRole('button', { name: `Xác nhận ${label.toLowerCase()}` }),
      )
      expect(dialog.getByText('Vui lòng nhập lý do.')).toBeInTheDocument()
      expect(request).not.toHaveBeenCalled()
      await userEvent.type(
        dialog.getByRole('textbox', { name: /Lý do/ }),
        'Lý do kiểm tra',
      )
      mock.failNextAction(400)
      await userEvent.click(
        dialog.getByRole('button', { name: `Xác nhận ${label.toLowerCase()}` }),
      )
      await dialog.findByText('Lỗi mô phỏng theo kịch bản.')
      expect(dialog.getByRole('textbox', { name: /Lý do/ })).toHaveValue(
        'Lý do kiểm tra',
      )
      await userEvent.click(
        dialog.getByRole('button', { name: `Xác nhận ${label.toLowerCase()}` }),
      )
      await waitForSuccess(label)
      expect(request).toHaveBeenLastCalledWith(10, {
        version: 0,
        reason: 'Lý do kiểm tra',
      })
    },
  )
  it('reassigns an in-progress ticket and unassigns with separate confirmations and reasons', async () => {
    const reassign = vi.spyOn(ticketApi, 'reassign')
    const unassign = vi.spyOn(ticketApi, 'unassign')
    await mount(13, { ...WORKFLOW_MOCK_MANAGER, roles: ['ADMIN'] })
    const dialog = await openDialog('Phân công lại')
    expect(
      dialog.getByText(/Kỹ thuật viên hiện tại: Phạm Dũng/),
    ).toBeInTheDocument()
    await userEvent.click(
      await dialog.findByRole('radio', { name: 'Chọn Nguyễn An' }),
    )
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận phân công lại' }),
    )
    expect(dialog.getByText('Vui lòng nhập lý do.')).toBeInTheDocument()
    await userEvent.type(
      dialog.getByRole('textbox', { name: /Lý do/ }),
      'Đổi ca trực',
    )
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận phân công lại' }),
    )
    await waitForSuccess('Phân công lại')
    expect(reassign).toHaveBeenCalledExactlyOnceWith(13, {
      version: 3,
      technicianId: 21,
      reason: 'Đổi ca trực',
    })
    const unassignment = await openDialog('Gỡ phân công')
    await userEvent.click(
      unassignment.getByRole('button', { name: 'Xác nhận gỡ phân công' }),
    )
    expect(unassignment.getByText('Vui lòng nhập lý do.')).toBeInTheDocument()
    await userEvent.type(
      unassignment.getByRole('textbox', { name: /Lý do/ }),
      'Cần xem xét lại',
    )
    await userEvent.click(
      unassignment.getByRole('button', { name: 'Xác nhận gỡ phân công' }),
    )
    await waitForSuccess('Gỡ phân công')
    expect(unassign).toHaveBeenCalledExactlyOnceWith(13, {
      version: 4,
      reason: 'Cần xem xét lại',
    })
    expect(
      screen.getByText(/Kỹ thuật viên hiện tại: Chưa phân công/),
    ).toBeInTheDocument()
  })
  it('searches, filters and paginates candidates; disables blocked candidates without override', async () => {
    const assign = vi.spyOn(ticketApi, 'assign')
    await mount(11)
    const dialog = await openDialog('Phân công')
    expect(
      await dialog.findByRole('radio', { name: 'Chọn Đỗ Khánh' }),
    ).toBeDisabled()
    expect(dialog.getByRole('radio', { name: 'Chọn Hoàng Lan' })).toBeDisabled()
    expect(dialog.getByRole('radio', { name: 'Chọn Bùi Minh' })).toBeDisabled()
    await userEvent.selectOptions(dialog.getByLabelText('Mỗi trang'), '5')
    await waitFor(() =>
      expect(
        dialog.queryByRole('radio', { name: 'Chọn Đỗ Khánh' }),
      ).not.toBeInTheDocument(),
    )
    await userEvent.click(dialog.getByRole('button', { name: 'Trang sau' }))
    expect(
      await dialog.findByRole('radio', { name: 'Chọn Đỗ Khánh' }),
    ).toBeDisabled()
    await userEvent.selectOptions(
      dialog.getByLabelText('Điều kiện phân công'),
      'BLOCKED',
    )
    expect(await dialog.findByText('Đã đạt sức chứa')).toBeInTheDocument()
    await userEvent.selectOptions(
      dialog.getByLabelText('Điều kiện phân công'),
      'ELIGIBLE',
    )
    await userEvent.type(
      dialog.getByRole('searchbox', { name: 'Tìm kỹ thuật viên' }),
      'Nguyễn',
    )
    await waitFor(() =>
      expect(
        dialog.getByRole('radio', { name: 'Chọn Nguyễn An' }),
      ).toBeEnabled(),
    )
    await userEvent.click(dialog.getByRole('radio', { name: 'Chọn Nguyễn An' }))
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    )
    await waitForSuccess('Phân công')
    expect(assign).toHaveBeenCalledExactlyOnceWith(11, {
      version: 1,
      technicianId: 21,
    })
  })
  it('refreshes changed capacity after 422 and resets selection requiring new choice', async () => {
    vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
    const assign = vi.spyOn(ticketApi, 'assign')
    await mount(11)
    const dialog = await openDialog('Phân công')
    await userEvent.click(
      await dialog.findByRole('radio', { name: 'Chọn Nguyễn An' }),
    )
    mock.store.candidates.find(
      (candidate) => candidate.technicianId === 21,
    ).maxActiveTickets = 1
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    )
    expect(assign).toHaveBeenCalledTimes(1)
    // 422 causes candidates to reload and clears stale selection
    await waitFor(() =>
      expect(
        dialog.getByRole('radio', { name: 'Chọn Nguyễn An' }),
      ).toBeDisabled(),
    )
    expect(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    ).toBeDisabled()
    // Select an eligible candidate
    await userEvent.click(dialog.getByRole('radio', { name: 'Chọn Phan Nam' }))
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    )
    await screen.findByText('Phân công thành công.')
    expect(assign).toHaveBeenLastCalledWith(11, {
      version: 1,
      technicianId: 29,
    })
  })
  it.each([undefined, 503])(
    'retains action input for network/server failure %s and retries only on confirmation',
    async (status) => {
      server.use(
        http.post('*/api/v1/tickets/10/reject', () =>
          status == null
            ? HttpResponse.error()
            : HttpResponse.json(
                { detail: 'Máy chủ tạm thời không khả dụng.' },
                { status },
              ),
        ),
      )
      const reject = vi.spyOn(ticketApi, 'reject')
      await mount()
      const dialog = await openDialog('Từ chối')
      await userEvent.type(dialog.getByRole('textbox'), 'Lý do được giữ')
      await userEvent.click(
        dialog.getByRole('button', { name: 'Xác nhận từ chối' }),
      )
      await dialog.findByText(
        status == null
          ? 'Không thể kết nối máy chủ. Vui lòng thử lại.'
          : 'Máy chủ tạm thời không khả dụng.',
      )
      expect(dialog.getByRole('textbox')).toHaveValue('Lý do được giữ')
      expect(reject).toHaveBeenCalledTimes(1)
      server.resetHandlers()
      await userEvent.click(
        dialog.getByRole('button', { name: 'Xác nhận từ chối' }),
      )
      await waitForSuccess('Từ chối')
    },
  )
  it('clears selection while searching and shows an empty candidate result', async () => {
    await mount(11)
    const dialog = await openDialog('Phân công')
    await userEvent.click(
      await dialog.findByRole('radio', { name: 'Chọn Nguyễn An' }),
    )
    expect(dialog.getByRole('status')).toHaveTextContent('Đã chọn: Nguyễn An')
    await userEvent.type(dialog.getByRole('searchbox'), 'không tìm thấy')
    expect(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    ).toBeDisabled()
    expect(dialog.queryByText(/Đã chọn:/)).not.toBeInTheDocument()
    expect(
      await dialog.findByText('Không có ứng viên phù hợp.'),
    ).toBeInTheDocument()
  })
  it('isolates a candidate failure from real detail and supports retry', async () => {
    server.use(
      http.get('*/api/v1/tickets/11/eligible-technicians', () =>
        HttpResponse.json({ detail: 'private' }, { status: 403 }),
      ),
    )
    await mount(11)
    const dialog = await openDialog('Phân công')
    await dialog.findByText(
      'Không thể tải ứng viên. Chi tiết phiếu vẫn có thể xem.',
    )
    expect(
      screen.getByRole('heading', { name: 'Lịch sử trạng thái' }),
    ).toBeInTheDocument()
    expect(screen.queryByText('private')).not.toBeInTheDocument()
    expect(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    ).toBeDisabled()
    server.resetHandlers()
    await userEvent.click(dialog.getByRole('button', { name: 'Thử lại' }))
    expect(
      await dialog.findByRole('radio', { name: 'Chọn Nguyễn An' }),
    ).toBeEnabled()
  })
  it.each([403, 404])(
    'keeps detail and inputs on an action HTTP %s failure',
    async (status) => {
      await mount()
      const dialog = await openDialog('Từ chối')
      await userEvent.type(dialog.getByRole('textbox'), 'Giữ lý do này')
      mock.failNextAction(status)
      await userEvent.click(
        dialog.getByRole('button', { name: 'Xác nhận từ chối' }),
      )
      await dialog.findByText(/Không thể thực hiện thao tác này/)
      expect(dialog.getByRole('textbox')).toHaveValue('Giữ lý do này')
      expect(
        screen.getByRole('heading', { name: 'Lịch sử trạng thái' }),
      ).toBeInTheDocument()
      expect(mock.store.tickets[0].version).toBe(0)
    },
  )
  it('locks a stale dialog on 409, reloads current detail and never replays the action', async () => {
    vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
    const review = vi.spyOn(ticketApi, 'review')
    await mount()
    const dialog = await openDialog('Bắt đầu xem xét')
    mock.failNextAction(409)
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận bắt đầu xem xét' }),
    )
    await dialog.findByText(/Phiếu đã thay đổi/)
    expect(
      dialog.getByRole('button', { name: 'Xác nhận bắt đầu xem xét' }),
    ).toBeDisabled()
    await userEvent.click(dialog.getByRole('button', { name: 'Tải lại phiếu' }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(
      await screen.findByRole('button', { name: 'Phân công', exact: true }),
    ).toBeEnabled()
    expect(
      screen.queryByRole('button', { name: 'Bắt đầu xem xét', exact: true }),
    ).not.toBeInTheDocument()
    expect(review).toHaveBeenCalledTimes(1)
  })
  it('keeps the version shown when opening a dialog even after a background detail refresh', async () => {
    vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
    const { client } = await mount(11)
    const dialog = await openDialog('Phân công')
    await dialog.findByRole('radio', { name: 'Chọn Nguyễn An' })
    await ticketApi.assign(11, { version: 1, technicianId: 21 })
    await client.refetchQueries({
      queryKey: ['tickets', 5, 'MANAGER', 'detail', '11'],
    })
    const assign = vi.spyOn(ticketApi, 'assign')
    await userEvent.click(dialog.getByRole('radio', { name: 'Chọn Nguyễn An' }))
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận phân công', exact: true }),
    )
    await dialog.findByText(/Phiếu đã thay đổi/)
    expect(assign).toHaveBeenCalledExactlyOnceWith(11, {
      version: 1,
      technicianId: 21,
    })
    expect(mock.store.tickets.find((item) => item.id === 11).version).toBe(2)
  })
  it('prevents duplicate submission and dismissal while pending', async () => {
    const slowMock = createTicketWorkflowMock({ actionDelay: 200 })
    server.use(...slowMock.handlers)
    const review = vi.spyOn(ticketApi, 'review')
    await mount()
    const dialog = await openDialog('Bắt đầu xem xét')
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận bắt đầu xem xét' }),
    )
    expect(dialog.getByRole('button', { name: 'Đang xử lý…' })).toBeDisabled()
    expect(dialog.getByRole('button', { name: 'Đóng' })).toBeDisabled()
    expect(
      dialog.queryByRole('button', { name: 'Close' }),
    ).not.toBeInTheDocument()
    fireEvent.click(dialog.getByRole('button', { name: 'Đang xử lý…' }))
    await waitForSuccess('Bắt đầu xem xét')
    expect(review).toHaveBeenCalledTimes(1)
  })
})
