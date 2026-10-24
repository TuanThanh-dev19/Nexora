import { StrictMode } from 'react'
import {
  cleanup,
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthContext } from '../../auth/context/auth-context'
import { TicketAttachmentPreview } from '../components/TicketAttachmentPreview'
import { TicketComments } from '../components/TicketComments'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, delay } from 'msw'
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
import { renderTicketApp } from '../testsupport/renderTicketApp'
import { demoScope } from '../../../shared/config/demoScope'

// Comments/feedback stay covered in isolation while the demo keeps them off.
vi.mock('../../../shared/config/demoScope', async (importOriginal) => {
  const { demoScope } = await importOriginal()
  return {
    demoScope: {
      ...demoScope,
      actions: { ...demoScope.actions, reopen: true },
      features: { ...demoScope.features, comments: true, feedback: true },
    },
  }
})

const owner = {
  id: 4,
  fullName: 'Người báo',
  status: 'ACTIVE',
  roles: ['REQUESTER'],
}
let actor = owner
const mock = createTicketWorkflowMock({ getActor: () => actor })
const server = setupServer(...mock.handlers)
const clients = []
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => {
  demoScope.features.comments = true
  demoScope.features.feedback = true
  actor = owner
  mock.reset()
  mock.store.feedback = []
  vi.stubEnv('DEV', true)
  vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'true')
  vi.stubGlobal(
    'URL',
    class extends URL {
      static createObjectURL = vi.fn(() => `blob:test-${Math.random()}`)
      static revokeObjectURL = vi.fn()
    },
  )
})
afterEach(() => {
  cleanup()
  clients.splice(0).forEach((client) => client.clear())
  server.resetHandlers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})
afterAll(() => server.close())
async function mount(id = 10) {
  const result = renderTicketApp(`/tickets/${id}`, actor)
  clients.push(result.client)
  await screen.findByText(`NX-2026-${String(id).padStart(6, '0')}`)
  return result
}
const section = (name) =>
  within(screen.getByRole('heading', { name, exact: true }).closest('.card'))

describe('Ticket Detail collaboration', () => {
  it('preserves the mixed-role reporter draft and submits PUBLIC after assignment loss', async () => {
    actor = { ...owner, roles: ['REQUESTER', 'TECHNICIAN'] }
    const ticket = mock.store.tickets.find((item) => item.id === 12)
    ticket.currentAssignment.technician.technicianId = actor.id
    const client = new QueryClient()
    clients.push(client)
    const view = (detail) => (
      <QueryClientProvider client={client}>
        <AuthContext.Provider
          value={{ user: actor, isAuthenticated: true, isChecking: false }}
        >
          <TicketComments ticket={detail} />
        </AuthContext.Provider>
      </QueryClientProvider>
    )
    const result = render(view(ticket))
    const post = vi.spyOn(ticketApi, 'addComment')
    await userEvent.selectOptions(
      await screen.findByLabelText('Phạm vi bình luận'),
      'INTERNAL',
    )
    const draft = 'Giữ bản nháp khi thay đổi người xử lý'
    await userEvent.type(screen.getByLabelText('Nội dung bình luận'), draft)
    ticket.currentAssignment = null
    ticket.status = 'UNDER_REVIEW'
    result.rerender(view({ ...ticket }))
    expect(
      screen.queryByRole('option', { name: 'Nội bộ' }),
    ).not.toBeInTheDocument()
    expect(screen.getByLabelText('Phạm vi bình luận')).toHaveValue('PUBLIC')
    expect(screen.getByLabelText('Nội dung bình luận')).toHaveValue(draft)
    await userEvent.click(screen.getByRole('button', { name: 'Gửi bình luận' }))
    await screen.findByText(draft)
    expect(post).toHaveBeenCalledWith(12, {
      content: draft,
      visibility: 'PUBLIC',
    })
    expect(mock.store.comments.at(-1)).toMatchObject({
      content: draft,
      visibility: 'PUBLIC',
    })
  })
  it('recovers after one decode-error retry without remounting the broken image while pending', async () => {
    const broken = new Blob(['invalid image'], { type: 'image/png' })
    const valid = new Blob(
      [new Uint8Array(mock.store.attachmentContents['10:1000'].bytes)],
      { type: 'image/png' },
    )
    let completeRetry
    vi.spyOn(ticketApi, 'attachmentContent')
      .mockResolvedValueOnce(broken)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            completeRetry = resolve
          }),
      )
    URL.createObjectURL.mockImplementation((blob) =>
      blob === broken ? 'blob:broken' : 'blob:valid',
    )
    await mount()
    await userEvent.click(
      screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
    )
    fireEvent.error(await screen.findByRole('img'))
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại ảnh' }))
    await screen.findByText('Đang tải ảnh...')
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Tải xuống' }),
    ).not.toBeInTheDocument()
    await waitFor(() => expect(completeRetry).toBeTypeOf('function'))
    await act(async () => completeRetry(valid))
    expect(await screen.findByRole('img')).toHaveAttribute('src', 'blob:valid')
    expect(screen.getByRole('button', { name: 'Tải xuống' })).toHaveAttribute(
      'href',
      'blob:valid',
    )
    expect(
      screen.queryByText('Không đọc được nội dung ảnh.'),
    ).not.toBeInTheDocument()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:broken')
  })
  it('creates and edits public comments with preserved authors/time and no internal selector', async () => {
    await mount()
    const comments = section('Bình luận')
    await comments.findByText('Xin kiểm tra thiết bị giúp tôi.')
    expect(comments.queryByText(/Ghi chú nội bộ/)).not.toBeInTheDocument()
    expect(
      comments.queryByRole('option', { name: 'Nội bộ' }),
    ).not.toBeInTheDocument()
    await userEvent.click(
      comments.getByRole('button', { name: 'Gửi bình luận' }),
    )
    expect(comments.getByText(/Nội dung phải có/)).toBeInTheDocument()
    await userEvent.type(
      comments.getByLabelText('Nội dung bình luận'),
      'Bổ sung thông tin',
    )
    await userEvent.click(
      comments.getByRole('button', { name: 'Gửi bình luận' }),
    )
    await comments.findByText('Bổ sung thông tin')
    expect(comments.getByLabelText('Nội dung bình luận')).toHaveValue('')
    const added = comments.getByText('Bổ sung thông tin').closest('li')
    await userEvent.click(
      within(added).getByRole('button', { name: /Sửa bình luận/ }),
    )
    const edit = comments.getByLabelText('Sửa nội dung bình luận')
    await userEvent.clear(edit)
    await userEvent.type(edit, 'Đã sửa nội dung')
    expect(
      comments.queryByLabelText('Phạm vi bình luận'),
    ).not.toBeInTheDocument()
    await userEvent.click(
      comments.getByRole('button', { name: 'Lưu bình luận' }),
    )
    await comments.findByText('Đã sửa nội dung')
    expect(comments.getByText(/Đã sửa \d/)).toBeInTheDocument()
  })
  it('shows manager internal comments and allows INTERNAL submission', async () => {
    actor = { id: 5, fullName: 'Quản lý', status: 'ACTIVE', roles: ['MANAGER'] }
    await mount()
    const comments = section('Bình luận')
    await comments.findByText('Ghi chú nội bộ cho nhóm xử lý.')
    await userEvent.selectOptions(
      comments.getByLabelText('Phạm vi bình luận'),
      'INTERNAL',
    )
    await userEvent.type(
      comments.getByLabelText('Nội dung bình luận'),
      'Thông tin nội bộ mới',
    )
    await userEvent.click(
      comments.getByRole('button', { name: 'Gửi bình luận' }),
    )
    await comments.findByText('Thông tin nội bộ mới')
    expect(mock.store.comments.at(-1).visibility).toBe('INTERNAL')
  })
  it('preserves a failed draft, displays server field errors and locks duplicate submits while pending', async () => {
    const post = vi.spyOn(ticketApi, 'addComment')
    await mount()
    const comments = section('Bình luận')
    await comments.findByLabelText('Nội dung bình luận')
    await userEvent.type(
      comments.getByLabelText('Nội dung bình luận'),
      'Bản nháp',
    )
    mock.failNextSubresource('comments', 400)
    await userEvent.click(
      comments.getByRole('button', { name: 'Gửi bình luận' }),
    )
    await comments.findByText('Dữ liệu không hợp lệ.')
    expect(comments.getByLabelText('Nội dung bình luận')).toHaveValue(
      'Bản nháp',
    )
    let finish
    server.use(
      http.post('*/api/v1/tickets/10/comments', async () => {
        await new Promise((resolve) => {
          finish = resolve
        })
        return HttpResponse.json({ id: 'saved' }, { status: 201 })
      }),
    )
    await userEvent.type(comments.getByLabelText('Nội dung bình luận'), ' mới')
    await userEvent.click(
      comments.getByRole('button', { name: 'Gửi bình luận' }),
    )
    await waitFor(() => expect(finish).toBeTypeOf('function'))
    expect(comments.getByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
    expect(comments.getByLabelText('Nội dung bình luận')).toBeDisabled()
    fireEvent.submit(
      comments.getByLabelText('Nội dung bình luận').closest('form'),
    )
    expect(post).toHaveBeenCalledTimes(2)
    finish()
    await comments.findByText(/Đã lưu bình luận trong bản mô phỏng/)
  })
  it('shows empty/loading/page states and preserves detail when comments fail', async () => {
    let failed = true
    server.use(
      http.get('*/api/v1/tickets/10/comments', async ({ request }) => {
        await delay(50)
        if (failed) return HttpResponse.json({}, { status: 403 })
        const page = Number(new URL(request.url).searchParams.get('page'))
        return HttpResponse.json({
          content:
            page === 0
              ? []
              : [
                  {
                    id: 'page-2',
                    author: { id: 5, fullName: 'Quản lý' },
                    content: 'Trang thứ hai',
                    visibility: 'PUBLIC',
                    createdAt: '2026-10-09T08:00:00Z',
                    editedAt: null,
                  },
                ],
          page,
          size: 20,
          totalElements: 21,
          totalPages: 2,
        })
      }),
    )
    await mount()
    const comments = section('Bình luận')
    expect(comments.getByText('Đang tải bình luận...')).toBeInTheDocument()
    await comments.findByRole('button', { name: 'Thử lại bình luận' })
    expect(
      screen.getByRole('heading', { name: 'Lịch sử trạng thái' }),
    ).toBeInTheDocument()
    failed = false
    await userEvent.click(
      comments.getByRole('button', { name: 'Thử lại bình luận' }),
    )
    await comments.findByText('Chưa có bình luận.')
    await userEvent.click(
      comments.getByRole('button', { name: 'Bình luận sau' }),
    )
    await comments.findByText('Trang thứ hai')
  })
  it('closes as owner with exact version and refreshes to eligible feedback', async () => {
    const close = vi.spyOn(ticketApi, 'close')
    await mount(15)
    await userEvent.click(
      screen.getByRole('button', { name: 'Đóng phiếu', exact: true }),
    )
    const dialog = within(await screen.findByRole('dialog'))
    expect(dialog.getByText(/Không bắt buộc/)).toBeInTheDocument()
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận đóng phiếu' }),
    )
    await screen.findByText('Đóng phiếu thành công.')
    expect(close).toHaveBeenCalledExactlyOnceWith(15, { version: 4 })
    expect(
      await section('Đánh giá').findByRole('button', { name: 'Gửi đánh giá' }),
    ).toBeEnabled()
    expect(
      screen.queryByRole('button', { name: 'Mở lại phiếu', exact: true }),
    ).not.toBeInTheDocument()
  })
  it('hides manager close and locks deferred reopen on 409 until reload', async () => {
    actor = { id: 5, fullName: 'Quản lý', status: 'ACTIVE', roles: ['MANAGER'] }
    await mount(15)
    expect(
      screen.queryByRole('button', { name: 'Đóng phiếu', exact: true }),
    ).not.toBeInTheDocument()
    let dialog
    await userEvent.click(
      screen.getByRole('button', { name: 'Mở lại phiếu', exact: true }),
    )
    dialog = within(await screen.findByRole('dialog'))
    await userEvent.type(dialog.getByLabelText(/^Lý do/), 'Thiết bị vẫn lỗi')
    mock.failNextAction(409)
    await userEvent.click(
      dialog.getByRole('button', { name: 'Xác nhận mở lại phiếu' }),
    )
    await dialog.findByText(/Phiếu đã thay đổi/)
    expect(
      dialog.getByRole('button', { name: 'Xác nhận mở lại phiếu' }),
    ).toBeDisabled()
    await userEvent.click(dialog.getByRole('button', { name: 'Tải lại phiếu' }))
    await waitFor(() =>
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument(),
    )
    expect(mock.store.tickets.find((ticket) => ticket.id === 15).status).toBe(
      'CLOSED',
    )
  })
  it('supports keyboard rating and immutable one-only feedback', async () => {
    await mount(17)
    const feedback = section('Đánh giá')
    await feedback.findByRole('button', { name: 'Gửi đánh giá' })
    await userEvent.click(
      feedback.getByRole('button', { name: 'Gửi đánh giá' }),
    )
    expect(feedback.getByText(/Vui lòng chọn mức đánh giá/)).toBeInTheDocument()
    const radio = feedback.getByRole('radio', { name: '1 sao' })
    radio.focus()
    await userEvent.keyboard(' ')
    await userEvent.keyboard('{ArrowRight}')
    expect(feedback.getByRole('radio', { name: '2 sao' })).toBeChecked()
    await userEvent.type(
      feedback.getByLabelText('Nhận xét (không bắt buộc)'),
      'Cảm ơn',
    )
    await userEvent.click(
      feedback.getByRole('button', { name: 'Gửi đánh giá' }),
    )
    await feedback.findByText('Đánh giá: 2/5')
    expect(feedback.getByText('Cảm ơn')).toBeInTheDocument()
    expect(
      feedback.queryByRole('button', { name: 'Gửi đánh giá' }),
    ).not.toBeInTheDocument()
    expect(
      section('Bình luận').queryByLabelText('Nội dung bình luận'),
    ).not.toBeInTheDocument()
  })
  it('renders existing feedback read-only without edit/delete or duplicate submit controls', async () => {
    mock.reset()
    await mount(17)
    const feedback = section('Đánh giá')
    await feedback.findByText('Đánh giá: 4/5')
    expect(
      feedback.getByText('Thiết bị hoạt động tốt sau khi sửa.'),
    ).toBeInTheDocument()
    expect(feedback.queryByRole('radio')).not.toBeInTheDocument()
    expect(
      feedback.queryByRole('button', { name: /Gửi|Sửa|Xóa/ }),
    ).not.toBeInTheDocument()
  })
  it('blocks feedback on failed reads and reloads an existing record after duplicate conflict', async () => {
    server.use(
      http.get('*/api/v1/tickets/17/feedback', () =>
        HttpResponse.json({}, { status: 503 }),
      ),
    )
    await mount(17)
    const feedback = section('Đánh giá')
    await feedback.findByRole('button', { name: 'Thử lại đánh giá' })
    expect(
      feedback.queryByRole('button', { name: 'Gửi đánh giá' }),
    ).not.toBeInTheDocument()
    server.resetHandlers()
    await userEvent.click(
      feedback.getByRole('button', { name: 'Thử lại đánh giá' }),
    )
    await feedback.findByRole('button', { name: 'Gửi đánh giá' })
    mock.store.feedback.push({
      id: 'existing',
      ticketId: 17,
      rating: 4,
      comment: 'Đã gửi ở tab khác',
      createdAt: '2026-10-09T08:00:00Z',
    })
    await userEvent.click(feedback.getByRole('radio', { name: '5 sao' }))
    await userEvent.click(
      feedback.getByRole('button', { name: 'Gửi đánh giá' }),
    )
    await feedback.findByText('Đánh giá: 4/5')
    expect(
      feedback.queryByRole('button', { name: 'Gửi đánh giá' }),
    ).not.toBeInTheDocument()
    expect(mock.store.feedback).toHaveLength(1)
  })
  it('previews authenticated bytes, downloads a blob URL and releases it on close/unmount', async () => {
    const result = await mount()
    const content = vi.spyOn(ticketApi, 'attachmentContent')
    await userEvent.click(
      screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
    )
    const image = await screen.findByRole('img', { name: 'minh-chứng.png' })
    const download = screen.getByRole('button', { name: 'Tải xuống' })
    expect(download).toHaveAttribute('download', 'minh-chứng.png')
    expect(download.getAttribute('href')).toBe(image.getAttribute('src'))
    expect(download.getAttribute('href')).toMatch(/^blob:/)
    expect(download.getAttribute('href')).not.toContain('token')
    expect(content).toHaveBeenCalledWith(10, 1000, expect.any(AbortSignal))
    const url = image.getAttribute('src')
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Đóng',
        exact: true,
      }),
    )
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith(url))
    await userEvent.click(
      screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
    )
    const next = (await screen.findByRole('img')).getAttribute('src')
    result.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(next)
  })
  it('isolates missing image and malformed-image errors with retry', async () => {
    await mount()
    mock.failNextSubresource('attachments', 404)
    await userEvent.click(
      screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
    )
    await screen.findByText('Tệp không còn khả dụng.')
    expect(screen.getByText('NX-2026-000010')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại ảnh' }))
    const image = await screen.findByRole('img')
    fireEvent.error(image)
    expect(screen.getByText('Không đọc được nội dung ảnh.')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Tải xuống' }),
    ).not.toBeInTheDocument()
  })
  it('cancels a pending image read when its modal closes', async () => {
    let signal
    vi.spyOn(ticketApi, 'attachmentContent').mockImplementation(
      (_ticketId, _attachmentId, requestSignal) => {
        signal = requestSignal
        return new Promise((_resolve, reject) =>
          signal.addEventListener('abort', () =>
            reject(new DOMException('Aborted', 'AbortError')),
          ),
        )
      },
    )
    await mount()
    await userEvent.click(
      screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
    )
    await screen.findByText('Đang tải ảnh...')
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Đóng',
        exact: true,
      }),
    )
    await waitFor(() => expect(signal.aborted).toBe(true))
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })
  it('releases every URL across StrictMode setup/cleanup and blob replacement', async () => {
    const client = new QueryClient()
    clients.push(client)
    const key = ['tickets', 4, 'REQUESTER', 'attachment-content', '10', '1000']
    client.setQueryData(key, new Blob(['first'], { type: 'image/png' }))
    const result = render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <AuthContext.Provider value={{ user: owner }}>
            <TicketAttachmentPreview
              ticketId={10}
              attachment={mock.store.tickets[0].attachments[0]}
              onHide={() => {}}
            />
          </AuthContext.Provider>
        </QueryClientProvider>
      </StrictMode>,
    )
    await screen.findByRole('img')
    await waitFor(() =>
      expect(URL.createObjectURL.mock.calls.length).toBeGreaterThanOrEqual(2),
    )
    const before = URL.createObjectURL.mock.calls.length
    client.setQueryData(key, new Blob(['replacement'], { type: 'image/png' }))
    await waitFor(() =>
      expect(URL.createObjectURL.mock.calls.length).toBeGreaterThan(before),
    )
    result.unmount()
    for (const { value } of URL.createObjectURL.mock.results)
      expect(URL.revokeObjectURL).toHaveBeenCalledWith(value)
  })
  it.each([false, true])(
    'keeps real images available without enabling other collaboration (production=%s)',
    async (production) => {
      demoScope.features.comments = false
      demoScope.features.feedback = false
      if (production) vi.stubEnv('DEV', false)
      else vi.stubEnv('VITE_TICKET_WORKFLOW_MOCK', 'false')
      const comments = vi.spyOn(ticketApi, 'comments')
      const feedback = vi.spyOn(ticketApi, 'feedback')
      const image = vi.spyOn(ticketApi, 'attachmentContent')
      await mount()
      expect(
        screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
      ).toBeEnabled()
      expect(
        screen.queryByRole('button', { name: 'Hủy phiếu', exact: true }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('heading', { name: 'Bình luận' }),
      ).not.toBeInTheDocument()
      expect(
        screen.queryByRole('heading', { name: 'Đánh giá' }),
      ).not.toBeInTheDocument()
      expect(comments).not.toHaveBeenCalled()
      expect(feedback).not.toHaveBeenCalled()
      expect(image).not.toHaveBeenCalled()
      await userEvent.click(
        screen.getByRole('button', { name: 'Xem ảnh minh-chứng.png' }),
      )
      await screen.findByRole('img', { name: 'minh-chứng.png' })
      expect(image).toHaveBeenCalled()
      expect(screen.getByRole('button', { name: 'Tải xuống' })).toHaveAttribute(
        'href',
        expect.stringContaining('blob:'),
      )
    },
  )
})
