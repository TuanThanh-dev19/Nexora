import { useSearchParams } from 'react-router'
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

export function readTicketSearchParams(search) {
  // Normalize sort only for the select control; the request keeps the raw query.
  const rawSort = search.get('sort') ?? 'createdAt,desc'
  const parts = rawSort.split(',')
  const sort =
    parts.length <= 2
      ? `${parts[0].trim()},${(parts[1] ?? 'asc').trim().toLowerCase()}`
      : rawSort
  return {
    size: search.get('size') ?? '20',
    sort,
    status: search.get('status') ?? '',
    priority: search.get('priority') ?? '',
    locationId: search.get('locationId') ?? '',
  }
}

export function useTicketSearchParams() {
  const [search, setSearch] = useSearchParams()
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
  return {
    params: readTicketSearchParams(search),
    requestSearch: search.toString(),
    update,
    reset: () => setSearch(new URLSearchParams()),
  }
}
