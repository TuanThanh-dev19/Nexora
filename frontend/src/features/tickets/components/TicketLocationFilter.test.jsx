import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
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
import { TicketLocationFilter } from './TicketLocationFilter'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
function show(locationId = '', onChange = vi.fn()) {
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } })
      }
    >
      <TicketLocationFilter locationId={locationId} onChange={onChange} />
    </QueryClientProvider>,
  )
  return onChange
}
const room = {
  id: 2,
  name: 'Phòng 201',
  code: 'R201',
  type: 'ROOM',
  active: false,
}
describe('TicketLocationFilter', () => {
  it('restores the selected inactive location and clearing explicitly changes the filter', async () => {
    server.use(http.get('*/api/v1/locations/2', () => HttpResponse.json(room)))
    const change = show('2')
    expect(
      await screen.findByRole('button', { name: /Phòng 201.*Ngừng sử dụng/ }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xóa lọc địa điểm' }))
    expect(change).toHaveBeenCalledWith('')
  })
  it('queries root and child locations including inactive records and selects an exact location', async () => {
    const seen = []
    server.use(
      http.get('*/api/v1/locations', ({ request }) => {
        const params = new URL(request.url).searchParams
        seen.push(Object.fromEntries(params))
        const roots = [
          { id: 1, name: 'Cơ sở A', code: 'A', type: 'CAMPUS', active: true },
        ]
        return HttpResponse.json(params.get('parentId') ? [room] : roots)
      }),
    )
    const change = show()
    fireEvent.click(screen.getByRole('button', { name: 'Chọn địa điểm' }))
    fireEvent.click(
      await screen.findByRole('button', { name: 'Xem bên trong Cơ sở A' }),
    )
    fireEvent.click(
      await screen.findByRole('button', { name: 'Chọn Phòng 201' }),
    )
    expect(change).toHaveBeenCalledWith('2')
    expect(seen).toEqual([
      { active: 'false' },
      { active: 'false', parentId: '1' },
    ])
  })
  it('keeps the selected filter when name lookup fails', async () => {
    server.use(
      http.get('*/api/v1/locations/2', () =>
        HttpResponse.json({}, { status: 404 }),
      ),
    )
    const change = show('2')
    expect(await screen.findByText(/Bộ lọc vẫn được giữ/)).toBeInTheDocument()
    expect(change).not.toHaveBeenCalled()
  })
})
