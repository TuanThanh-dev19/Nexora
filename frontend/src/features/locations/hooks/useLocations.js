import { useQuery } from '@tanstack/react-query'
import { locationApi } from '../api/locationApi'

const retry = (count, error) =>
  count < 1 && (error?.response?.status == null || error.response.status >= 500)

export const locationKeys = {
  all: ['locations'],
  lists: () => [...locationKeys.all, 'list'],
  children: (parentId, active) => [
    ...locationKeys.all,
    'children',
    parentId !== undefined ? parentId : '__none__',
    Boolean(active),
  ],
  detail: (id) => [...locationKeys.all, 'detail', id],
}

/**
 * Hook to query child locations of a parent (or roots when parentId is null).
 * Supports both legacy signature: useLocations(parentId, enabled)
 * and options signature: useLocations(parentId, { active, enabled })
 */
export function useLocations(parentId, options = {}) {
  const isLegacy = typeof options === 'boolean'
  const enabled = isLegacy ? options : (options.enabled ?? true)
  const active = isLegacy ? false : Boolean(options.active)

  return useQuery({
    queryKey: locationKeys.children(parentId, active),
    queryFn: ({ signal }) => locationApi.list(parentId, active, signal),
    enabled,
    retry,
  })
}

export function useLocation(id) {
  return useQuery({
    queryKey: locationKeys.detail(id),
    queryFn: ({ signal }) => locationApi.getById(id, signal),
    enabled: Boolean(id),
    retry,
  })
}
