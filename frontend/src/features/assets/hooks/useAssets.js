import { useQuery } from '@tanstack/react-query'
import { assetApi } from '../api/assetApi'

export const assetKeys = {
  all: ['assets'],
  lists: () => [...assetKeys.all, 'list'],
  list: (params) => [...assetKeys.lists(), params ?? {}],
  details: () => [...assetKeys.all, 'detail'],
  detail: (id) => [...assetKeys.details(), id ? String(id) : null],
  histories: () => [...assetKeys.all, 'history'],
  history: (id) => [...assetKeys.histories(), id ? String(id) : null],
}

export function useAssets(params = {}, enabled = true) {
  return useQuery({
    queryKey: assetKeys.list(params),
    queryFn: ({ signal }) => assetApi.list(params, signal),
    enabled,
  })
}

export function useAsset(id, enabled = true) {
  return useQuery({
    queryKey: assetKeys.detail(id),
    queryFn: ({ signal }) => assetApi.getById(id, signal),
    enabled: Boolean(id) && enabled,
  })
}

export function useAssetHistory(id, enabled = true) {
  return useQuery({
    queryKey: assetKeys.history(id),
    queryFn: ({ signal }) => assetApi.getStatusHistory(id, signal),
    enabled: Boolean(id) && enabled,
  })
}
