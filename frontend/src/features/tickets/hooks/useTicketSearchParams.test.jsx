import { describe, expect, it } from 'vitest'
import { parseTicketSearchParams } from './useTicketSearchParams'

describe('ticket search contract', () => {
  it('uses shared defaults and parses supported filters', () => {
    expect(parseTicketSearchParams(new URLSearchParams()).params).toEqual({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })
    expect(
      parseTicketSearchParams(
        new URLSearchParams(
          'page=2&size=100&sort=ticketNumber&status=REOPENED&priority=URGENT&locationId=9223372036854775807',
        ),
      ).params,
    ).toEqual({
      page: 2,
      size: 100,
      sort: 'ticketNumber,asc',
      status: 'REOPENED',
      priority: 'URGENT',
      locationId: '9223372036854775807',
    })
  })
  it.each([
    'page=-1',
    'page=1.5',
    'page=2147483648',
    'page=2147483647',
    'size=0',
    'size=101',
    'sort=title,asc',
    'sort=createdAt,no',
    'sort=createdAt,desc,extra',
    'status=submitted',
    'priority=',
    'locationId=0',
    'locationId=9223372036854775808',
    'keyword=anything',
    'page=0&page=1',
    'sort=createdAt&sort=updatedAt',
  ])('rejects %s without silently dropping it', (search) => {
    expect(parseTicketSearchParams(new URLSearchParams(search))).toEqual({
      params: null,
      error: { response: { status: 400 } },
    })
  })
})
