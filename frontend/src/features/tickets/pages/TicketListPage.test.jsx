import {
  cleanup,
  fireEvent,
  screen,
  waitFor,
  within,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, delay, http } from 'msw'
import { setupServer } from 'msw/node'
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest'
import { ticketApi } from '../api/ticketApi'
import { renderTicketApp } from '../testsupport/renderTicketApp'
import { page, summary } from '../testsupport/ticketFixtures'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  vi.restoreAllMocks()
})
afterAll(() => server.close())

describe('TicketListPage', () => {
  it('submits with Enter or button, trims keyword, resets page and preserves filters/sort', async () => {
    const requests = []
    server.use(
      http.get('*/api/v1/tickets', ({ request }) => {
        requests.push(Object.fromEntries(new URL(request.url).searchParams))
        return HttpResponse.json(page())
      }),
    )
    renderTicketApp(
      '/tickets?page=3&status=SUBMITTED&priority=HIGH&sort=ticketNumber,asc',
    )
    await screen.findByText(summary.ticketNumber)
    const user = userEvent.setup()
    await user.type(
      screen.getByLabelText('Tìm theo mã hoặc tiêu đề'),
      '  Máy chiếu  ',
    )
    expect(requests).toHaveLength(1)
    await user.keyboard('{Enter}')
    await waitFor(() =>
      expect(requests.at(-1)).toMatchObject({
        keyword: 'Máy chiếu',
        page: '0',
        status: 'SUBMITTED',
        priority: 'HIGH',
        sort: 'ticketNumber,asc',
      }),
    )
    await user.clear(screen.getByLabelText('Tìm theo mã hoặc tiêu đề'))
    await user.type(
      screen.getByLabelText('Tìm theo mã hoặc tiêu đề'),
      'NX-2026',
    )
    await user.click(
      screen.getByRole('button', { name: 'Tìm kiếm', exact: true }),
    )
    await waitFor(() => expect(requests.at(-1).keyword).toBe('NX-2026'))
    await user.click(
      screen.getByRole('button', { name: 'Đặt lại bộ lọc', exact: true }),
    )
    await waitFor(() => expect(requests.at(-1)).toEqual({}))
    expect(screen.getByLabelText('Tìm theo mã hoặc tiêu đề')).toHaveValue('')
  })
  it('restores keyword from a shared URL and back from detail', async () => {
    server.use(
      http.get('*/api/v1/tickets', () => HttpResponse.json(page())),
      http.get('*/api/v1/tickets/10', () =>
        HttpResponse.json({}, { status: 404 }),
      ),
    )
    renderTicketApp('/tickets?keyword=Máy+chiếu&priority=HIGH&page=1')
    expect(screen.getByLabelText('Tìm theo mã hoặc tiêu đề')).toHaveValue(
      'Máy chiếu',
    )
    fireEvent.click(
      await screen.findByRole('link', { name: summary.ticketNumber }),
    )
    const back = screen.getByRole('link', { name: '← Danh sách phiếu' })
    expect(back.getAttribute('href')).toContain('keyword=Máy+chiếu')
    fireEvent.click(back)
    expect(
      await screen.findByLabelText('Tìm theo mã hoặc tiêu đề'),
    ).toHaveValue('Máy chiếu')
    expect(screen.getByLabelText('Mức ưu tiên')).toHaveValue('HIGH')
  })
  it('cancels a slow old search and never presents its rows as the new results', async () => {
    let oldSignal, release
    const originalSearch = ticketApi.search
    vi.spyOn(ticketApi, 'search').mockImplementation((search, signal) => {
      if (new URLSearchParams(search).get('keyword') === 'slow')
        oldSignal = signal
      return originalSearch(search, signal)
    })
    server.use(
      http.get('*/api/v1/tickets', async ({ request }) => {
        const keyword = new URL(request.url).searchParams.get('keyword')
        if (keyword === 'slow') {
          await new Promise((r) => {
            release = r
          })
          return HttpResponse.json(page())
        }
        if (keyword === 'new')
          return HttpResponse.json(
            page([], { totalElements: 0, totalPages: 0 }),
          )
        return HttpResponse.json(page())
      }),
    )
    renderTicketApp()
    await screen.findByText(summary.ticketNumber)
    fireEvent.change(screen.getByLabelText('Tìm theo mã hoặc tiêu đề'), {
      target: { value: 'slow' },
    })
    fireEvent.click(
      screen.getByRole('button', { name: 'Tìm kiếm', exact: true }),
    )
    await waitFor(() => expect(release).toBeDefined())
    expect(screen.queryByText(summary.ticketNumber)).not.toBeInTheDocument()
    expect(screen.getByText('Đang tải phiếu yêu cầu...')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Tìm theo mã hoặc tiêu đề'), {
      target: { value: 'new' },
    })
    fireEvent.click(
      screen.getByRole('button', { name: 'Tìm kiếm', exact: true }),
    )
    await screen.findByText('Không có phiếu yêu cầu phù hợp.')
    await waitFor(() => expect(oldSignal.aborted).toBe(true))
    release()
    expect(screen.queryByText(summary.ticketNumber)).not.toBeInTheDocument()
    expect(
      within(screen.getByLabelText('Phân trang')).getByText(/0 phiếu/),
    ).toBeInTheDocument()
  })
  it('rejects an overlong keyword locally without replacing applied search results', async () => {
    const requests = []
    server.use(
      http.get('*/api/v1/tickets', ({ request }) => {
        requests.push(request.url)
        return HttpResponse.json(page())
      }),
    )
    renderTicketApp()
    await screen.findByText(summary.ticketNumber)
    fireEvent.change(screen.getByLabelText('Tìm theo mã hoặc tiêu đề'), {
      target: { value: 'x'.repeat(201) },
    })
    expect(
      screen.getByRole('button', { name: 'Tìm kiếm', exact: true }),
    ).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent('200 ký tự')
    expect(requests).toHaveLength(1)
  })
  it('renders loading followed by real API data and no workflow buttons', async () => {
    server.use(
      http.get('*/api/v1/tickets', async () => {
        await delay(30)
        return HttpResponse.json(page())
      }),
    )
    const { container } = renderTicketApp()
    expect(screen.getByText('Đang tải phiếu yêu cầu...')).toBeInTheDocument()
    expect(
      await screen.findByRole('link', { name: summary.ticketNumber }),
    ).toHaveAttribute('href', '/tickets/10')
    const table = screen.getByRole('table')
    for (const text of [
      'Đã gửi',
      'Cao',
      summary.category.name,
      summary.location.name,
      'PROJ-03',
    ])
      expect(within(table).getByText(text)).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: 'Hạn SLA' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('columnheader', { name: 'Ngày tạo' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', {
        name: /Review|Assign|Start|Resolve|Create/i,
      }),
    ).toBeNull()
    expect(container.innerHTML).not.toContain('Bearer')
  })
  it('renders empty data and an out-of-range page can return to the first page', async () => {
    server.use(
      http.get('*/api/v1/tickets', ({ request }) =>
        HttpResponse.json(
          page([], {
            page: Number(new URL(request.url).searchParams.get('page')),
            totalElements: 1,
            totalPages: 1,
          }),
        ),
      ),
    )
    renderTicketApp('/tickets?page=99')
    expect(
      await screen.findByText('Không có phiếu yêu cầu phù hợp.'),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Trang đầu' }))
    await waitFor(() =>
      expect(screen.getByLabelText('URL hiện tại')).toHaveTextContent('page=0'),
    )
  })
  it.each([400, 401, 403, 404, 503])(
    'renders safe HTTP %s without server details or fake rows',
    async (status) => {
      server.use(
        http.get('*/api/v1/tickets', () =>
          HttpResponse.json({ detail: 'secret SQL text' }, { status }),
        ),
      )
      renderTicketApp()
      expect(await screen.findByRole('alert')).toBeInTheDocument()
      expect(screen.queryByText('secret SQL text')).toBeNull()
      expect(screen.queryByText(summary.ticketNumber)).toBeNull()
      if (status >= 500)
        expect(
          screen.getByRole('button', { name: 'Thử lại' }),
        ).toBeInTheDocument()
    },
  )
  it('retries a network failure and allows manual recovery', async () => {
    let calls = 0
    server.use(
      http.get('*/api/v1/tickets', () => {
        calls++
        return HttpResponse.error()
      }),
    )
    renderTicketApp()
    expect(
      await screen.findByText('Không thể kết nối máy chủ. Vui lòng thử lại.'),
    ).toBeInTheDocument()
    expect(calls).toBe(2)
    server.use(http.get('*/api/v1/tickets', () => HttpResponse.json(page())))
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText(summary.ticketNumber)).toBeInTheDocument()
  })
  it('restores filters from URL and sends page/filter/sort changes to the API', async () => {
    const requests = []
    server.use(
      http.get('*/api/v1/locations/2', () =>
        HttpResponse.json({
          id: 2,
          name: 'Phòng 201',
          code: 'R201',
          active: true,
        }),
      ),
      http.get('*/api/v1/tickets', ({ request }) => {
        const params = Object.fromEntries(new URL(request.url).searchParams)
        requests.push(params)
        return HttpResponse.json(
          page([summary], {
            page: Number(params.page),
            size: Number(params.size),
            totalPages: 4,
            totalElements: 80,
          }),
        )
      }),
    )
    const initial =
      '/tickets?page=1&size=20&sort=updatedAt,desc&status=SUBMITTED&priority=HIGH&locationId=2&keyword=chiếu'
    renderTicketApp(initial)
    await screen.findByText(summary.ticketNumber)
    expect(screen.getByLabelText('Trạng thái')).toHaveValue('SUBMITTED')
    expect(screen.getByLabelText('Mức ưu tiên')).toHaveValue('HIGH')
    expect(requests[0]).toEqual({
      page: '1',
      size: '20',
      sort: 'updatedAt,desc',
      status: 'SUBMITTED',
      priority: 'HIGH',
      locationId: '2',
      keyword: 'chiếu',
    })
    fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }))
    await waitFor(() =>
      expect(requests.at(-1)).toMatchObject({ page: '2', keyword: 'chiếu' }),
    )
    fireEvent.change(screen.getByLabelText('Mức ưu tiên'), {
      target: { value: 'URGENT' },
    })
    await waitFor(() =>
      expect(requests.at(-1)).toMatchObject({
        page: '0',
        priority: 'URGENT',
        locationId: '2',
      }),
    )
    fireEvent.change(screen.getByLabelText('Số phiếu mỗi trang'), {
      target: { value: '50' },
    })
    await waitFor(() => expect(requests.at(-1).size).toBe('50'))
    fireEvent.change(screen.getByLabelText('Sắp xếp'), {
      target: { value: 'ticketNumber,asc' },
    })
    await waitFor(() => expect(requests.at(-1).sort).toBe('ticketNumber,asc'))
    fireEvent.click(screen.getByRole('button', { name: 'Browser back' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Sắp xếp')).toHaveValue('updatedAt,desc'),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Browser forward' }))
    await waitFor(() =>
      expect(screen.getByLabelText('Sắp xếp')).toHaveValue('ticketNumber,asc'),
    )
  })
  it.each([
    'unknown=unsupported',
    'page=0&page=1',
    'priority=',
    'size=101',
    'sort=title,asc',
    'locationId=9223372036854775808',
    'locationId=9007199254740993&unknown=unsupported',
  ])(
    'sends %s unchanged, renders backend 400 and resets explicitly',
    async (search) => {
      const requests = []
      server.use(
        http.get('*/api/v1/locations/:locationId', () =>
          HttpResponse.json({
            id: 2,
            name: 'Địa điểm',
            code: 'L',
            active: true,
          }),
        ),
        http.get('*/api/v1/tickets', ({ request }) => {
          const query = new URL(request.url).searchParams
          requests.push([...query])
          if ([...query].length > 0)
            return HttpResponse.json({}, { status: 400 })
          return HttpResponse.json(page([]))
        }),
      )
      renderTicketApp(`/tickets?${search}`)
      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent('Tham số không hợp lệ')
      expect(requests).toEqual([[...new URLSearchParams(search)]])
      expect(screen.getByLabelText('URL hiện tại')).toHaveTextContent(
        `/tickets?${search}`,
      )
      fireEvent.click(
        within(alert).getByRole('button', { name: 'Đặt lại bộ lọc' }),
      )
      expect(
        await screen.findByText('Không có phiếu yêu cầu phù hợp.'),
      ).toBeInTheDocument()
      expect(requests.at(-1)).toEqual([])
      expect(screen.getByLabelText('URL hiện tại')).toHaveTextContent(
        /^\/tickets$/,
      )
    },
  )
  it('shows a network failure for an invalid URL when no backend response arrives', async () => {
    server.use(http.get('*/api/v1/tickets', () => HttpResponse.error()))
    renderTicketApp('/tickets?unknown=unsupported')
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể kết nối máy chủ',
    )
    expect(screen.queryByText(/Tham số không hợp lệ/)).toBeNull()
    expect(screen.getByLabelText('URL hiện tại')).toHaveTextContent(
      'unknown=unsupported',
    )
  })
  it('keeps the list search when navigating to detail and back', async () => {
    server.use(
      http.get('*/api/v1/tickets', () => HttpResponse.json(page())),
      http.get('*/api/v1/tickets/10', () =>
        HttpResponse.json({}, { status: 404 }),
      ),
    )
    renderTicketApp('/tickets?priority=HIGH')
    fireEvent.click(
      await screen.findByRole('link', { name: summary.ticketNumber }),
    )
    const back = screen.getByRole('link', { name: '← Danh sách phiếu' })
    expect(back).toHaveAttribute('href', '/tickets?priority=HIGH')
    fireEvent.click(back)
    expect(await screen.findByLabelText('Mức ưu tiên')).toHaveValue('HIGH')
  })
  it('does not show cached data from another viewer', async () => {
    const { QueryClient } = await import('@tanstack/react-query')
    const client = new QueryClient()
    client.setQueryData(['tickets', 99, 'ADMIN', 'list', ''], page())
    server.use(
      http.get('*/api/v1/tickets', async () => {
        await delay(30)
        return HttpResponse.json(page([]))
      }),
    )
    renderTicketApp('/tickets', { id: 4, roles: ['REQUESTER'] }, client)
    expect(screen.queryByText(summary.ticketNumber)).toBeNull()
    expect(
      await screen.findByText('Không có phiếu yêu cầu phù hợp.'),
    ).toBeInTheDocument()
    expect(
      within(screen.getByLabelText('Phân trang')).getByText(/0 phiếu/),
    ).toBeInTheDocument()
  })
})
