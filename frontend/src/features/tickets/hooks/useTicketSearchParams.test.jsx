import { act, cleanup, renderHook } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import {
  readTicketSearchParams,
  useTicketSearchParams,
} from './useTicketSearchParams'

afterEach(cleanup)

describe('ticket search controls and URL', () => {
  it('reads control defaults without adding them to the request', () => {
    expect(readTicketSearchParams(new URLSearchParams())).toEqual({
      size: '20',
      sort: 'createdAt,desc',
      status: '',
      priority: '',
      locationId: '',
    })
  })
  it('reads filters as strings and normalizes sort only for display', () => {
    const search = new URLSearchParams(
      'page=2&size=100&sort= ticketNumber , ASC &status=REOPENED&priority=URGENT&locationId=9223372036854775807',
    )
    expect(readTicketSearchParams(search)).toEqual({
      size: '100',
      sort: 'ticketNumber,asc',
      status: 'REOPENED',
      priority: 'URGENT',
      locationId: '9223372036854775807',
    })
    expect(search.get('sort')).toBe(' ticketNumber , ASC ')
    expect(
      readTicketSearchParams(new URLSearchParams('sort=ticketNumber')).sort,
    ).toBe('ticketNumber,asc')
  })
  it('preserves raw invalid query data until the user explicitly updates or resets it', () => {
    const original =
      'page=0&page=1&keyword=unsupported&priority=&locationId=9223372036854775808'
    const { result } = renderHook(() => useTicketSearchParams(), {
      wrapper: ({ children }) => (
        <MemoryRouter initialEntries={[`/tickets?${original}`]}>
          {children}
        </MemoryRouter>
      ),
    })
    expect(result.current.requestSearch).toBe(original)
    expect(result.current.params.locationId).toBe('9223372036854775808')
    act(() => result.current.update({ page: 2 }))
    const paged = new URLSearchParams(result.current.requestSearch)
    expect(paged.getAll('page')).toEqual(['2'])
    expect(paged.get('keyword')).toBe('unsupported')
    expect(paged.has('priority')).toBe(true)
    expect(paged.get('priority')).toBe('')
    act(() => result.current.update({ status: 'SUBMITTED', locationId: '' }))
    const filtered = new URLSearchParams(result.current.requestSearch)
    expect(filtered.get('page')).toBe('0')
    expect(filtered.get('status')).toBe('SUBMITTED')
    expect(filtered.has('locationId')).toBe(false)
    expect(filtered.get('keyword')).toBe('unsupported')
    act(() => result.current.reset())
    expect(result.current.requestSearch).toBe('')
    expect(result.current.params.size).toBe('20')
  })
})
