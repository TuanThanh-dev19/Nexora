import { useQuery } from '@tanstack/react-query'
import { locationApi } from '../api/locationApi'

const retry = (count, error) =>
  count < 1 && (error?.response?.status == null || error.response.status >= 500)
export function useLocations(parentId, enabled = true) {
  return useQuery({
    queryKey: ['locations', 'children', parentId ?? null],
    queryFn: ({ signal }) => locationApi.list(parentId, signal),
    enabled,
    retry,
  })
}
export function useLocation(id) {
  return useQuery({
    queryKey: ['locations', 'detail', id],
    queryFn: ({ signal }) => locationApi.getById(id, signal),
    enabled: Boolean(id),
    retry,
  })
}
