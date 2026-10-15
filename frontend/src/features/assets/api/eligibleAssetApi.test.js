import { beforeEach, describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { eligibleAssetApi, mockEligibleAssetAdapter } from './eligibleAssetApi'

describe('eligibleAssetApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  describe('mockEligibleAssetAdapter contract tests', () => {
    it('returns only ACTIVE and UNDER_MAINTENANCE assets for the exact location', () => {
      const result = mockEligibleAssetAdapter(10)
      expect(result.totalElements).toBe(2)
      expect(result.content.map((a) => a.id)).toEqual([101, 102])
      expect(result.content.every((a) => a.location.id === 10)).toBe(true)
      expect(
        result.content.every((a) =>
          ['ACTIVE', 'UNDER_MAINTENANCE'].includes(a.status),
        ),
      ).toBe(true)

      // Ensure INACTIVE (103) and RETIRED (104) are NOT included
      expect(result.content.some((a) => a.id === 103)).toBe(false)
      expect(result.content.some((a) => a.id === 104)).toBe(false)

      // Ensure parent location 1 asset (901) is NOT included
      expect(result.content.some((a) => a.id === 901)).toBe(false)
    })

    it('returns correct assets for another location (location 20)', () => {
      const result = mockEligibleAssetAdapter(20)
      expect(result.totalElements).toBe(2)
      expect(result.content.map((a) => a.id)).toEqual([201, 202])
      expect(result.content.some((a) => a.id === 203)).toBe(false) // INACTIVE
    })

    it('filters by equipmentTypeId', () => {
      const result = mockEligibleAssetAdapter(10, { equipmentTypeId: 1 })
      expect(result.totalElements).toBe(1)
      expect(result.content[0].assetCode).toBe('AST-L10-01')
    })

    it('filters by query across code and description', () => {
      const result = mockEligibleAssetAdapter(10, { query: 'Sony' })
      expect(result.totalElements).toBe(1)
      expect(result.content[0].assetCode).toBe('AST-L10-01')

      const resultByCode = mockEligibleAssetAdapter(10, { query: 'AST-L10-02' })
      expect(resultByCode.totalElements).toBe(1)
      expect(resultByCode.content[0].id).toBe(102)
    })

    it('handles pagination correctly', () => {
      const page0 = mockEligibleAssetAdapter(10, { page: 0, size: 1 })
      expect(page0.content.length).toBe(1)
      expect(page0.page).toBe(0)
      expect(page0.size).toBe(1)
      expect(page0.totalElements).toBe(2)
      expect(page0.totalPages).toBe(2)

      const page1 = mockEligibleAssetAdapter(10, { page: 1, size: 1 })
      expect(page1.content.length).toBe(1)
      expect(page1.page).toBe(1)
      expect(page1.content[0].id).toBe(102)
    })

    it('returns empty page when location has no eligible assets', () => {
      const result = mockEligibleAssetAdapter(999)
      expect(result.totalElements).toBe(0)
      expect(result.totalPages).toBe(0)
      expect(result.content).toEqual([])
    })
  })

  describe('eligibleAssetApi.listEligible HTTP integration', () => {
    it('returns empty page immediately if locationId is missing', async () => {
      const result = await eligibleAssetApi.listEligible(null)
      expect(result.content).toEqual([])
      expect(result.totalElements).toBe(0)
    })

    it('sends correct GET request to /locations/{locationId}/eligible-assets', async () => {
      const mockData = {
        content: [{ id: 101, assetCode: 'AST-01' }],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }
      vi.spyOn(httpClient, 'get').mockResolvedValueOnce({ data: mockData })

      const res = await eligibleAssetApi.listEligible(10, {
        page: 0,
        size: 20,
        equipmentTypeId: 2,
        query: 'test',
      })

      expect(httpClient.get).toHaveBeenCalledWith(
        '/locations/10/eligible-assets',
        {
          params: {
            page: 0,
            size: 20,
            equipmentTypeId: 2,
            query: 'test',
          },
          signal: undefined,
        },
      )
      expect(res).toEqual(mockData)
    })

    it('falls back to mock adapter when endpoint returns 404', async () => {
      vi.spyOn(httpClient, 'get').mockRejectedValueOnce({
        response: { status: 404 },
      })

      const res = await eligibleAssetApi.listEligible(10)
      expect(res.totalElements).toBe(2)
      expect(res.content.length).toBe(2)
    })

    it('throws error when status is 400 Bad Request', async () => {
      vi.spyOn(httpClient, 'get').mockRejectedValueOnce({
        response: { status: 400, data: { detail: 'Invalid query parameter' } },
      })

      await expect(eligibleAssetApi.listEligible(10)).rejects.toMatchObject({
        response: { status: 400 },
      })
    })
  })
})
