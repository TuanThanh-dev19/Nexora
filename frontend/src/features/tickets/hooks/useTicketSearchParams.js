import { useSearchParams } from 'react-router'
import { TICKET_STATUSES } from '../constants/ticketStatuses'
import { PRIORITIES } from '../../../shared/constants/priorities'

const keys = ['page', 'size', 'sort', 'status', 'priority', 'locationId']
export const TICKET_SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Mới tạo trước' },
  { value: 'createdAt,asc', label: 'Cũ trước' },
  { value: 'updatedAt,desc', label: 'Mới cập nhật trước' },
  { value: 'updatedAt,asc', label: 'Cập nhật cũ trước' },
  { value: 'ticketNumber,asc', label: 'Số phiếu tăng dần' },
  { value: 'ticketNumber,desc', label: 'Số phiếu giảm dần' },
]

export function isPositiveId(value) {
  return (
    typeof value === 'string' &&
    /^[0-9]+$/.test(value) &&
    BigInt(value) > 0n &&
    BigInt(value) <= 9223372036854775807n
  )
}

export function parseTicketSearchParams(search) {
  const fail = () => ({ params: null, error: { response: { status: 400 } } })
  for (const key of search.keys()) {
    if (!keys.includes(key) || search.getAll(key).length !== 1) return fail()
  }
  const integer = (key, fallback) => {
    if (!search.has(key)) return fallback
    const raw = search.get(key)
    return /^[0-9]+$/.test(raw) ? Number(raw) : NaN
  }
  const page = integer('page', 0)
  const size = integer('size', 20)
  if (
    !Number.isInteger(page) ||
    page < 0 ||
    page > 2147483647 ||
    !Number.isInteger(size) ||
    size < 1 ||
    size > 100 ||
    page * size > 2147483647
  )
    return fail()
  const rawSort = search.get('sort') ?? 'createdAt,desc'
  const parts = rawSort.split(',')
  const field = parts[0].trim()
  const direction = parts.length === 1 ? 'asc' : parts[1].trim().toLowerCase()
  if (
    parts.length > 2 ||
    !['createdAt', 'updatedAt', 'ticketNumber'].includes(field) ||
    !['asc', 'desc'].includes(direction)
  )
    return fail()
  const params = { page, size, sort: `${field},${direction}` }
  for (const [key, allowed] of [
    ['status', TICKET_STATUSES],
    ['priority', PRIORITIES],
  ]) {
    if (search.has(key)) {
      if (!allowed.includes(search.get(key))) return fail()
      params[key] = search.get(key)
    }
  }
  if (search.has('locationId')) {
    if (!isPositiveId(search.get('locationId'))) return fail()
    params.locationId = search.get('locationId')
  }
  return { params, error: null }
}

export function useTicketSearchParams() {
  const [search, setSearch] = useSearchParams()
  const parsed = parseTicketSearchParams(search)
  function update(changes) {
    const next = new URLSearchParams(search)
    for (const [key, value] of Object.entries(changes)) {
      if (value === '' || value == null) next.delete(key)
      else next.set(key, String(value))
    }
    if (Object.keys(changes).some((key) => key !== 'page'))
      next.set('page', '0')
    setSearch(next)
  }
  return { ...parsed, update, reset: () => setSearch(new URLSearchParams()) }
}
