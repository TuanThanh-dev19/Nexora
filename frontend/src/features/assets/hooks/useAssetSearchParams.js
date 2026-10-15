import { useSearchParams } from 'react-router'
import { ASSET_STATUSES } from '../constants/assetStatuses'
import { ALLOWED_ASSET_SORT_FIELDS } from '../constants/assetSorts'

export function isPositiveId(value) {
  return (
    typeof value === 'string' &&
    /^[0-9]+$/.test(value) &&
    BigInt(value) > 0n &&
    BigInt(value) <= 9223372036854775807n
  )
}

function normalizeSort(rawSort) {
  if (!rawSort) return 'createdAt,desc'
  const parts = rawSort.split(',')
  const field = parts[0]?.trim()
  if (!ALLOWED_ASSET_SORT_FIELDS.has(field)) {
    return 'createdAt,desc'
  }
  const direction = parts[1]?.trim().toLowerCase() === 'asc' ? 'asc' : 'desc'
  return `${field},${direction}`
}

export function readAssetSearchParams(search) {
  const rawPage = search.get('page')
  const page = rawPage && /^[0-9]+$/.test(rawPage) ? rawPage : '0'

  const rawSize = search.get('size')
  const size = rawSize && /^[0-9]+$/.test(rawSize) ? rawSize : '20'

  const sort = normalizeSort(search.get('sort'))

  const rawLocationId = search.get('locationId')
  const locationId =
    rawLocationId && isPositiveId(rawLocationId) ? rawLocationId : ''

  const rawStatus = search.get('status')
  const status =
    rawStatus && ASSET_STATUSES.includes(rawStatus) ? rawStatus : ''

  return {
    page,
    size,
    sort,
    locationId,
    status,
  }
}

export function useAssetSearchParams() {
  const [search, setSearch] = useSearchParams()
  const params = readAssetSearchParams(search)

  function update(changes) {
    const next = new URLSearchParams()

    // Carry over current sanitized params
    for (const [key, value] of Object.entries(params)) {
      if (value !== '' && value != null) {
        next.set(key, String(value))
      }
    }

    // Apply changes
    for (const [key, value] of Object.entries(changes)) {
      if (value === '' || value == null) {
        next.delete(key)
      } else {
        next.set(key, String(value))
      }
    }

    // Reset page to 0 if any filter or sort changed
    const nonPageChanged = Object.keys(changes).some((k) => k !== 'page')
    if (nonPageChanged) {
      next.set('page', '0')
    }

    setSearch(next)
  }

  function reset() {
    setSearch(new URLSearchParams())
  }

  // Construct request params object strictly matching API parameters
  const requestParams = {
    page: Number(params.page),
    size: Number(params.size),
    sort: params.sort,
    ...(params.locationId ? { locationId: Number(params.locationId) } : {}),
    ...(params.status ? { status: params.status } : {}),
  }

  return {
    params,
    requestParams,
    update,
    reset,
  }
}
