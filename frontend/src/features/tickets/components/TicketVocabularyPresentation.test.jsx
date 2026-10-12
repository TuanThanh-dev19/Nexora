import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import {
  TICKET_STATUSES,
  TICKET_STATUS_LABELS,
} from '../constants/ticketStatuses'
import { TicketFilters } from './TicketFilters'
import { TicketStatusBadge } from './TicketStatusBadge'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

describe('ticket vocabulary presentation', () => {
  it('offers every canonical ticket status as a filter', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <TicketFilters status="" onChange={vi.fn()} />
      </QueryClientProvider>,
    )

    const statusSelect = screen.getByRole('combobox', {
      name: 'Trạng thái',
    })
    const options = within(statusSelect).getAllByRole('option')

    expect(options.map((option) => option.value)).toEqual([
      '',
      ...TICKET_STATUSES,
    ])
  })

  it('renders a badge for every canonical ticket status', () => {
    render(
      <div>
        {TICKET_STATUSES.map((status) => (
          <TicketStatusBadge key={status} status={status} />
        ))}
      </div>,
    )

    for (const status of TICKET_STATUSES) {
      expect(screen.getByText(TICKET_STATUS_LABELS[status])).toBeInTheDocument()
    }
  })
})
