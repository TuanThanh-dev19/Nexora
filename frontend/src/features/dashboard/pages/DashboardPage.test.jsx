import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, delay, http } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { AuthContext } from '../../auth/context/auth-context'
import { DashboardPage } from './DashboardPage'

const SUMMARY_URL = '*/api/v1/dashboard/summary'
const successSummary = {
  openTickets: 12,
  inProgressTickets: 4,
  slaAtRiskTickets: 2,
  resolvedThisWeek: 7,
  generatedAt: '2026-10-09T10:30:00+07:00',
}

const server = setupServer()

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderDashboard(roles = ['REQUESTER']) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={{ user: { id: 10, roles } }}>
        <DashboardPage />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  it('renders the loading state for all four metrics', () => {
    server.use(
      http.get(SUMMARY_URL, async () => {
        await delay('infinite')
        return HttpResponse.json(successSummary)
      }),
    )

    renderDashboard()

    expect(screen.getByLabelText('Đang tải Phiếu đang mở')).toBeInTheDocument()
    expect(screen.getByLabelText('Đang tải Đang xử lý')).toBeInTheDocument()
    expect(
      screen.getByLabelText('Đang tải Có nguy cơ trễ SLA'),
    ).toBeInTheDocument()
    expect(
      screen.getByLabelText('Đang tải Đã giải quyết tuần này'),
    ).toBeInTheDocument()
  })

  it('renders the success state from the API contract', async () => {
    server.use(http.get(SUMMARY_URL, () => HttpResponse.json(successSummary)))

    renderDashboard(['ADMIN'])

    expect(await screen.findByText('12')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(
      screen.getByText('Tổng quan toàn bộ phiếu yêu cầu.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/Cập nhật:.*10:30:00.*Asia\/Bangkok/),
    ).toBeInTheDocument()
  })

  it('renders a dedicated empty state when all metrics are zero', async () => {
    server.use(
      http.get(SUMMARY_URL, () =>
        HttpResponse.json({
          ...successSummary,
          openTickets: 0,
          inProgressTickets: 0,
          slaAtRiskTickets: 0,
          resolvedThisWeek: 0,
        }),
      ),
    )

    renderDashboard()

    expect(
      await screen.findByText(
        'Chưa có hoạt động phiếu yêu cầu trong phạm vi của bạn.',
      ),
    ).toBeInTheDocument()
    expect(screen.getAllByText('0')).toHaveLength(4)
  })

  it('renders the error state without substituting fake production metrics', async () => {
    server.use(
      http.get(SUMMARY_URL, () =>
        HttpResponse.json({ title: 'Unavailable' }, { status: 503 }),
      ),
    )

    renderDashboard()

    expect(
      await screen.findByText(/Chưa tải được số liệu tổng quan/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(4)
  })

  it.each([
    [['REQUESTER'], 'Phiếu yêu cầu do chính bạn gửi.'],
    [
      ['TECHNICIAN'],
      'Phiếu được phân công cho bạn hiện tại hoặc trong lịch sử.',
    ],
    [['MANAGER'], 'Tổng quan toàn bộ phiếu yêu cầu.'],
    [
      ['REQUESTER', 'TECHNICIAN'],
      'Phiếu được phân công cho bạn hiện tại hoặc trong lịch sử.',
    ],
    [['TECHNICIAN', 'ADMIN'], 'Tổng quan toàn bộ phiếu yêu cầu.'],
  ])('renders role-aware scope copy for %s', async (roles, expectedCopy) => {
    server.use(http.get(SUMMARY_URL, () => HttpResponse.json(successSummary)))

    renderDashboard(roles)

    expect(await screen.findByText(expectedCopy)).toBeInTheDocument()
  })

  it('retries explicitly and replaces the error with contract values', async () => {
    let attempts = 0
    server.use(
      http.get(SUMMARY_URL, () =>
        ++attempts === 1
          ? HttpResponse.json({}, { status: 503 })
          : HttpResponse.json(successSummary),
      ),
    )
    renderDashboard()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Thử lại' }),
    )
    expect(await screen.findByText('12')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(attempts).toBe(2)
  })
})
