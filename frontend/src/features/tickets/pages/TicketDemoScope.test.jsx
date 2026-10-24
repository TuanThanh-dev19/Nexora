import {
  act,
  cleanup,
  fireEvent,
  renderHook,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
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
import { AuthContext } from '../../auth/context/auth-context'
import { ticketApi } from '../api/ticketApi'
import {
  useTicketComments,
  useTicketFeedback,
} from '../hooks/useTicketCollaboration'
import { useTicketWorkflow } from '../hooks/useTicketWorkflow'
import { createTicketWorkflowMock } from '../mocks/ticketWorkflowHandlers'
import { renderTicketApp } from '../testsupport/renderTicketApp'

const owner = { id: 4, status: 'ACTIVE', roles: ['REQUESTER'] }
let actor = owner
const mock = createTicketWorkflowMock({
  getActor: () => actor,
  actionDelay: 100,
})
const server = setupServer(...mock.handlers)
const clients = []
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  actor = owner
  mock.reset()
  vi.stubEnv('DEV', true)
  vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
})
afterEach(() => {
  cleanup()
  clients.splice(0).forEach((client) => client.clear())
  server.resetHandlers()
  vi.restoreAllMocks()
  vi.unstubAllEnvs()
})
afterAll(() => server.close())

async function mount(id = 15) {
  const result = renderTicketApp(`/tickets/${id}`, actor)
  clients.push(result.client)
  await screen.findByRole('heading', {
    name: `NX-2026-${String(id).padStart(6, '0')}`,
  })
  return result
}

async function closeDialog() {
  await userEvent.click(
    screen.getByRole('button', { name: 'Đóng phiếu', exact: true }),
  )
  return within(await screen.findByRole('dialog'))
}

describe('real API demo scope', () => {
  it.each([
    ['REQUESTER'],
    ['REQUESTER', 'MANAGER'],
    ['REQUESTER', 'ADMIN'],
    ['REQUESTER', 'TECHNICIAN'],
  ])(
    'lets the reporter close with role union %j while mocks are off',
    async (...roles) => {
      actor = { ...owner, roles }
      const close = vi.spyOn(ticketApi, 'close')
      const { client } = await mount()
      const invalidate = vi.spyOn(client, 'invalidateQueries')
      const dialog = await closeDialog()
      expect(dialog.getByText(/Không bắt buộc/)).toBeInTheDocument()
      await userEvent.type(dialog.getByRole('textbox'), '  Đã kiểm tra  ')
      await userEvent.click(
        dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
      )
      await screen.findByText('Đóng phiếu thành công.')
      expect(close).toHaveBeenCalledExactlyOnceWith(15, {
        version: 4,
        reason: 'Đã kiểm tra',
      })
      expect(screen.getAllByText('Đã đóng').length).toBeGreaterThan(0)
      expect(screen.getByText('Thời gian đóng')).toBeInTheDocument()
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: ['tickets', 4, [...roles].sort().join(',')],
      })
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ['dashboard'] })
      expect(
        screen.queryByRole('button', { name: 'Đóng phiếu', exact: true }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByText(/thành công trong bản mô phỏng/),
      ).not.toBeInTheDocument()
      await userEvent.click(
        screen.getByRole('link', { name: /Danh sách phiếu/ }),
      )
      const number = await screen.findByRole('link', { name: /NX-2026-000015/ })
      expect(
        within(number.closest('tr')).getByText('Đã đóng'),
      ).toBeInTheDocument()
    },
  )

  it.each([['MANAGER'], ['ADMIN'], ['TECHNICIAN'], ['TECHNICIAN', 'MANAGER']])(
    'never gives non-Requester roles %j a close control',
    async (...roles) => {
      actor = { id: 5, status: 'ACTIVE', roles }
      // Isolate presentation permissions on a detail the server has made visible.
      server.use(
        http.get('*/api/v1/tickets/15', () =>
          HttpResponse.json(
            mock.store.tickets.find((ticket) => ticket.id === 15),
          ),
        ),
      )
      await mount()
      expect(
        screen.queryByRole('button', { name: 'Đóng phiếu', exact: true }),
      ).not.toBeInTheDocument()
    },
  )

  it.each(['SUBMITTED', 'IN_PROGRESS', 'CLOSED'])(
    'hides reporter close from %s',
    async (status) => {
      mock.store.tickets.find((ticket) => ticket.id === 15).status = status
      await mount()
      expect(
        screen.queryByRole('button', { name: 'Đóng phiếu', exact: true }),
      ).not.toBeInTheDocument()
    },
  )

  it.each([false, true])(
    'hides deferred controls and sends no deferred reads (mock flag=%s)',
    async (mockFlag) => {
      vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', String(mockFlag))
      const ticket = mock.store.tickets.find((ticket) => ticket.id === 13)
      actor = {
        id: ticket.currentAssignment.technician.technicianId,
        status: 'ACTIVE',
        roles: ['TECHNICIAN', 'MANAGER'],
      }
      const comments = vi.spyOn(ticketApi, 'comments')
      const feedback = vi.spyOn(ticketApi, 'feedback')
      const upload = vi.spyOn(ticketApi, 'uploadWorkResult')
      await mount(13)
      expect(
        screen.getByRole('button', { name: 'Thêm nhật ký', exact: true }),
      ).toBeEnabled()
      for (const name of [
        'Từ chối',
        'Hủy phiếu',
        'Phân công lại',
        'Gỡ phân công',
        'Mở lại phiếu',
        'Đóng phiếu',
      ]) {
        expect(
          screen.queryByRole('button', { name, exact: true }),
        ).not.toBeInTheDocument()
      }
      expect(
        screen.queryByRole('heading', { name: 'Bình luận' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('heading', { name: 'Đánh giá' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('heading', { name: 'Ảnh kết quả', exact: true }),
      ).toBeInTheDocument()
      expect(comments).not.toHaveBeenCalled()
      expect(feedback).not.toHaveBeenCalled()
      expect(upload).not.toHaveBeenCalled()
    },
  )

  it('blocks deferred hooks and mutations even when invoked directly', async () => {
    actor = { ...owner, roles: ['REQUESTER', 'TECHNICIAN', 'MANAGER'] }
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    clients.push(client)
    const ticket = {
      ...mock.store.tickets.find((ticket) => ticket.id === 13),
      reporter: owner,
      currentAssignment: {
        endedAt: null,
        technician: { technicianId: owner.id },
      },
    }
    const calls = [
      'comments',
      'feedback',
      'addComment',
      'addFeedback',
      'reject',
      'cancel',
      'reassign',
      'unassign',
      'reopen',
    ].map((method) => vi.spyOn(ticketApi, method))
    const { result } = renderHook(
      () => ({
        comments: useTicketComments(ticket, 0),
        feedback: useTicketFeedback(ticket),
        workflow: useTicketWorkflow(),
      }),
      {
        wrapper: ({ children }) => (
          <QueryClientProvider client={client}>
            <AuthContext.Provider
              value={{ user: actor, isAuthenticated: true, isChecking: false }}
            >
              {children}
            </AuthContext.Provider>
          </QueryClientProvider>
        ),
      },
    )
    await act(async () => {
      await expect(
        result.current.comments.mutation.mutateAsync({
          payload: { content: 'No request', visibility: 'PUBLIC' },
        }),
      ).rejects.toThrow()
      await expect(
        result.current.feedback.mutation.mutateAsync({ rating: 5 }),
      ).rejects.toThrow()
      for (const action of [
        'reject',
        'cancel',
        'reassign',
        'unassign',
        'reopen',
      ]) {
        await expect(
          result.current.workflow.mutation.mutateAsync({
            action,
            ticketId: ticket.id,
            ticket,
            payload: { version: ticket.version },
          }),
        ).rejects.toThrow()
      }
    })
    for (const call of calls) expect(call).not.toHaveBeenCalled()
  })

  it('keeps the confirmation version across refresh and requires reload plus a fresh confirmation after 409', async () => {
    const close = vi.spyOn(ticketApi, 'close')
    const { client } = await mount()
    let dialog = await closeDialog()
    mock.store.tickets.find((ticket) => ticket.id === 15).version = 5
    await act(async () =>
      client.refetchQueries({
        queryKey: ['tickets', 4, 'REQUESTER', 'detail', '15'],
      }),
    )
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
    )
    await dialog.findByText(/Phiếu đã thay đổi/)
    expect(close).toHaveBeenCalledExactlyOnceWith(15, { version: 4 })
    expect(
      dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
    ).toBeDisabled()
    fireEvent.submit(
      dialog
        .getByRole('button', { name: 'Xác nhận đóng phiếu' })
        .closest('form'),
    )
    expect(close).toHaveBeenCalledTimes(1)
    await userEvent.click(dialog.getByRole('button', { name: 'Tải lại phiếu' }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(close).toHaveBeenCalledTimes(1)
    dialog = await closeDialog()
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
    )
    await screen.findByText('Đóng phiếu thành công.')
    expect(close).toHaveBeenLastCalledWith(15, { version: 5 })
  })

  it('prevents repeated close submissions while pending', async () => {
    const close = vi.spyOn(ticketApi, 'close')
    await mount()
    const dialog = await closeDialog()
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
    )
    expect(dialog.getByRole('button', { name: 'Đang xử lý…' })).toBeDisabled()
    expect(
      dialog.getByRole('button', { name: 'Đóng', exact: true }),
    ).toBeDisabled()
    fireEvent.submit(
      dialog.getByRole('button', { name: 'Đang xử lý…' }).closest('form'),
    )
    await screen.findByText('Đóng phiếu thành công.')
    expect(close).toHaveBeenCalledExactlyOnceWith(15, { version: 4 })
  })

  it('retains optional reason on an API failure without retrying automatically', async () => {
    server.use(
      http.post('*/api/v1/tickets/15/close', () =>
        HttpResponse.json({ detail: 'Thử lại sau.' }, { status: 503 }),
      ),
    )
    const close = vi.spyOn(ticketApi, 'close')
    await mount()
    const dialog = await closeDialog()
    await userEvent.type(dialog.getByRole('textbox'), 'Đã kiểm tra')
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
    )
    await dialog.findByText('Thử lại sau.')
    expect(dialog.getByRole('textbox')).toHaveValue('Đã kiểm tra')
    expect(close).toHaveBeenCalledTimes(1)
  })
})
