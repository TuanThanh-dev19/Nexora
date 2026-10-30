import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import { TicketReportPage } from './TicketReportPage'
import { reportApi } from '../api/reportApi'

vi.mock('../api/reportApi', () => ({
  reportApi: {
    getTicketReport: vi.fn(),
    exportTicketReportCsv: vi.fn(),
  },
}))

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <TicketReportPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const mockReportData = {
  summary: {
    fromDate: '2026-10-24',
    toDate: '2026-10-30',
    timeZone: 'Asia/Bangkok',
    generatedAt: '2026-10-30T15:00:00+07:00',
    totalTickets: 2,
    overdueTickets: 1,
    statusCounts: {
      SUBMITTED: 1,
      UNDER_REVIEW: 0,
      ASSIGNED: 0,
      IN_PROGRESS: 1,
      RESOLVED: 0,
      REOPENED: 0,
      CLOSED: 0,
      REJECTED: 0,
      CANCELLED: 0,
    },
  },
  tickets: {
    content: [
      {
        id: 101,
        ticketNumber: 'TK-101',
        title: 'Hỏng máy lạnh phòng Lab',
        status: 'IN_PROGRESS',
        priority: 'HIGH',
        location: { id: 1, name: 'Phòng Lab 1' },
        category: { id: 2, name: 'Điện lạnh' },
        reporter: { id: 3, fullName: 'Nguyễn Văn A' },
        slaDueAt: '2026-10-30T12:00:00+07:00',
        createdAt: '2026-10-25T08:00:00+07:00',
        updatedAt: '2026-10-25T09:00:00+07:00',
      },
      {
        id: 102,
        ticketNumber: 'TK-102',
        title: 'Bóng đèn chập chờn',
        status: 'SUBMITTED',
        priority: 'NORMAL',
        location: { id: 2, name: 'Phòng học 202' },
        category: { id: 3, name: 'Chiếu sáng' },
        reporter: { id: 4, fullName: 'Trần Thị B' },
        slaDueAt: '2026-10-31T17:00:00+07:00',
        createdAt: '2026-10-26T10:00:00+07:00',
        updatedAt: '2026-10-26T10:00:00+07:00',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
  },
}

describe('TicketReportPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    reportApi.getTicketReport.mockResolvedValue(mockReportData)
  })

  it('renders default heading, helper notice, summary cards, and ticket rows', async () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Báo cáo Ticket' })).toBeInTheDocument()
    expect(
      screen.getByText('Ticket được tạo trong khoảng ngày đã chọn; trạng thái cập nhật tại thời điểm lập báo cáo.'),
    ).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('TK-101')).toBeInTheDocument()
    })

    expect(screen.getByText('Hỏng máy lạnh phòng Lab')).toBeInTheDocument()
    expect(screen.getByText('Bóng đèn chập chờn')).toBeInTheDocument()
    expect(screen.getByText('Danh sách Ticket (2)')).toBeInTheDocument()
    expect(screen.getByText('Đang quá hạn SLA')).toBeInTheDocument()
  })

  it('validates date range: rejects fromDate > toDate', async () => {
    const user = userEvent.setup()
    renderPage()

    const fromInput = screen.getByLabelText(/Từ ngày/)
    const toInput = screen.getByLabelText(/Đến ngày/)
    const applyBtn = screen.getByRole('button', { name: /Áp dụng/ })

    await user.clear(fromInput)
    await user.type(fromInput, '2026-10-30')
    await user.clear(toInput)
    await user.type(toInput, '2026-10-20')

    await user.click(applyBtn)

    expect(
      screen.getByText('Ngày bắt đầu phải nhỏ hơn hoặc bằng ngày kết thúc.'),
    ).toBeInTheDocument()
  })

  it('validates date range: rejects range over 92 days', async () => {
    const user = userEvent.setup()
    renderPage()

    const fromInput = screen.getByLabelText(/Từ ngày/)
    const toInput = screen.getByLabelText(/Đến ngày/)
    const applyBtn = screen.getByRole('button', { name: /Áp dụng/ })

    await user.clear(fromInput)
    await user.type(fromInput, '2026-01-01')
    await user.clear(toInput)
    await user.type(toInput, '2026-05-01')

    await user.click(applyBtn)

    expect(
      screen.getByText('Khoảng thời gian báo cáo tối đa là 92 ngày.'),
    ).toBeInTheDocument()
  })

  it('exports CSV using applied filters, not unapplied input changes', async () => {
    const user = userEvent.setup()
    const fakeBlob = new Blob(['csv content'], { type: 'text/csv' })
    reportApi.exportTicketReportCsv.mockResolvedValue({
      data: fakeBlob,
      headers: {
        'content-disposition': 'attachment; filename="ticket-report_custom.csv"',
      },
    })

    // Mock window.URL.createObjectURL
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:mock-url')
    window.URL.revokeObjectURL = vi.fn()

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('TK-101')).toBeInTheDocument()
    })

    // Change input value without clicking "Áp dụng"
    const fromInput = screen.getByLabelText(/Từ ngày/)
    await user.clear(fromInput)
    await user.type(fromInput, '2026-10-01')

    // Click Export CSV
    const exportBtn = screen.getByRole('button', { name: /CSV/ })
    await user.click(exportBtn)

    await waitFor(() => {
      expect(reportApi.exportTicketReportCsv).toHaveBeenCalled()
    })

    // Notice that export used appliedFilters, NOT '2026-10-01'
    const exportCallArg = reportApi.exportTicketReportCsv.mock.calls[0][0]
    expect(exportCallArg.fromDate).not.toBe('2026-10-01')
  })

  it('handles export error (e.g. 10000 limit exceeded) cleanly', async () => {
    const user = userEvent.setup()
    const errorResponse = {
      response: {
        status: 422,
        data: {
          detail: 'The export query matches 10005 tickets, exceeding the maximum limit of 10,000 tickets.',
        },
      },
    }
    reportApi.exportTicketReportCsv.mockRejectedValue(errorResponse)

    renderPage()

    const exportBtn = screen.getByRole('button', { name: /CSV/ })
    await user.click(exportBtn)

    await waitFor(() => {
      expect(
        screen.getByText('The export query matches 10005 tickets, exceeding the maximum limit of 10,000 tickets.'),
      ).toBeInTheDocument()
    })
  })

  it('renders empty state when totalTickets is 0', async () => {
    reportApi.getTicketReport.mockResolvedValue({
      summary: {
        fromDate: '2026-10-24',
        toDate: '2026-10-30',
        timeZone: 'Asia/Bangkok',
        generatedAt: '2026-10-30T15:00:00+07:00',
        totalTickets: 0,
        overdueTickets: 0,
        statusCounts: {},
      },
      tickets: {
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
    })

    renderPage()

    await waitFor(() => {
      expect(
        screen.getByText('Không tìm thấy Ticket nào trong khoảng thời gian đã chọn.'),
      ).toBeInTheDocument()
    })
  })
})

