import { useQuery } from '@tanstack/react-query'
import { eligibleAssetApi } from '../api/eligibleAssetApi'

const retry = (count, error) =>
  count < 1 && (error?.response?.status == null || error.response.status >= 500)

export const eligibleAssetKeys = {
  all: ['eligible-assets'],
  location: (locationId) => [
    ...eligibleAssetKeys.all,
    'location',
    locationId ?? null,
  ],
  list: (locationId, params = {}) => [
    ...eligibleAssetKeys.location(locationId),
    {
      page: params.page ?? 0,
      size: params.size ?? 20,
      equipmentTypeId: params.equipmentTypeId ?? null,
      query: params.query ?? '',
    },
  ],
}

/**
 * Hook to query eligible assets for a given location.
 * @param {number|string} locationId
 * @param {Object} [params]
 * @param {number} [params.page=0]
 * @param {number} [params.size=20]
 * @param {number} [params.equipmentTypeId]
 * @param {string} [params.query]
 * @param {boolean} [params.enabled=true]
 */
export function useEligibleAssets(locationId, params = {}) {
  const { enabled = true, retry: customRetry, ...apiParams } = params
  const isEnabled = Boolean(locationId) && enabled

  return useQuery({
    queryKey: eligibleAssetKeys.list(locationId, apiParams),
    queryFn: ({ signal }) =>
      eligibleAssetApi.listEligible(locationId, apiParams, signal),
    enabled: isEnabled,
    retry: customRetry !== undefined ? customRetry : retry,
  })
}
