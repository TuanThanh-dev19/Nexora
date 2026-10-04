import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
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

    expect(screen.getByLabelText('Loading Open tickets')).toBeInTheDocument()
    expect(screen.getByLabelText('Loading In progress')).toBeInTheDocument()
    expect(screen.getByLabelText('Loading SLA at risk')).toBeInTheDocument()
    expect(
      screen.getByLabelText('Loading Resolved this week'),
    ).toBeInTheDocument()
  })

  it('renders the success state from the API contract', async () => {
    server.use(http.get(SUMMARY_URL, () => HttpResponse.json(successSummary)))

    renderDashboard(['ADMIN'])

    expect(await screen.findByText('12')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.getByText('Activity across all tickets.')).toBeInTheDocument()
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
        'No ticket activity is available for this dashboard yet.',
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
      await screen.findByText('Dashboard metrics are unavailable right now.'),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'Try again' }),
    ).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(4)
  })

  it.each([
    [['REQUESTER'], 'Tickets you submitted.'],
    [['TECHNICIAN'], 'Tickets currently or previously assigned to you.'],
    [['MANAGER'], 'Activity across all tickets.'],
  ])('renders role-aware scope copy for %s', async (roles, expectedCopy) => {
    server.use(http.get(SUMMARY_URL, () => HttpResponse.json(successSummary)))

    renderDashboard(roles)

    expect(await screen.findByText(expectedCopy)).toBeInTheDocument()
  })
})
