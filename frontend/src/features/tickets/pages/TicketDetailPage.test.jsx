import { cleanup, screen } from '@testing-library/react'
import { HttpResponse, delay, http } from 'msw'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { renderTicketApp } from '../testsupport/renderTicketApp'
import { detail } from '../testsupport/ticketFixtures'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())

describe('TicketDetailPage', () => {
  it('loads description, stored form and full read-only detail', async () => {
    server.use(
      http.get('*/api/v1/tickets/10', async () => {
        await delay(30)
        return HttpResponse.json(detail)
      }),
    )
    const { container } = renderTicketApp('/tickets/10')
    expect(screen.getByText('Đang tải chi tiết phiếu...')).toBeInTheDocument()
    expect(await screen.findByText(detail.ticketNumber)).toBeInTheDocument()
    expect(screen.getByText('Phiên bản 1 · Đã lưu trữ')).toBeInTheDocument()
    expect(screen.getByText(/Mô tả lỗi/)).toBeInTheDocument()
    expect(screen.getByText('9007199254740993')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Lịch sử trạng thái' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Tệp đính kèm' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', {
        name: /Review|Assign|Start|Resolve|Create/i,
      }),
    ).toBeNull()
    expect(container.querySelector('script')).toBeNull()
    expect(container.innerHTML).not.toContain('storageKey')
  })
  it.each([403, 404])(
    'shows the same safe message for HTTP %s and hides cached detail',
    async (status) => {
      const { QueryClient } = await import('@tanstack/react-query')
      const client = new QueryClient()
      client.setQueryData(['tickets', 4, 'REQUESTER', 'detail', '10'], detail)
      server.use(
        http.get('*/api/v1/tickets/10', () =>
          HttpResponse.json(
            { detail: 'private existence information' },
            { status },
          ),
        ),
      )
      renderTicketApp('/tickets/10', undefined, client)
      expect(
        await screen.findByText('Không thể truy cập phiếu yêu cầu này.'),
      ).toBeInTheDocument()
      expect(screen.queryByText(detail.ticketNumber)).toBeNull()
      expect(screen.queryByText('private existence information')).toBeNull()
    },
  )
  it('renders a network error', async () => {
    server.use(http.get('*/api/v1/tickets/10', () => HttpResponse.error()))
    renderTicketApp('/tickets/10')
    expect(
      await screen.findByText('Không thể kết nối máy chủ. Vui lòng thử lại.'),
    ).toBeInTheDocument()
  })
  it('rejects an invalid ID without calling the API', () => {
    renderTicketApp('/tickets/not-a-number')
    expect(screen.getByRole('alert')).toHaveTextContent('Tham số không hợp lệ')
  })
})
