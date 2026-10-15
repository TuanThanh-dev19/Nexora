import { describe, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { assetApi } from './assetApi'

vi.mock('../../../shared/api/httpClient', () => ({
  httpClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}))

describe('assetApi', () => {
  it('list requests GET /assets with only allowed params', async () => {
    httpClient.get.mockResolvedValueOnce({
      data: { content: [], totalElements: 0 },
    })

    const params = {
      page: '0',
      size: '20',
      sort: 'createdAt,desc',
      locationId: '5',
      status: 'ACTIVE',
      unsupported: 'should-be-omitted',
    }

    await assetApi.list(params)

    expect(httpClient.get).toHaveBeenCalledWith('/assets', {
      params: {
        page: 0,
        size: 20,
        sort: 'createdAt,desc',
        locationId: 5,
        status: 'ACTIVE',
      },
      signal: undefined,
    })
  })

  it('getById requests GET /assets/:id', async () => {
    httpClient.get.mockResolvedValueOnce({
      data: { id: 10, assetCode: 'AST-001' },
    })

    const result = await assetApi.getById(10)

    expect(httpClient.get).toHaveBeenCalledWith('/assets/10', {
      signal: undefined,
    })
    expect(result.id).toBe(10)
  })

  it('getStatusHistory requests GET /assets/:id/status-history', async () => {
    httpClient.get.mockResolvedValueOnce({ data: [] })

    await assetApi.getStatusHistory(10)

    expect(httpClient.get).toHaveBeenCalledWith('/assets/10/status-history', {
      signal: undefined,
    })
  })

  it('create sends trimmed payload with dates and IDs', async () => {
    httpClient.post.mockResolvedValueOnce({
      data: { id: 1, assetCode: 'AST-001' },
    })

    await assetApi.create({
      assetCode: '  AST-001  ',
      equipmentTypeId: '2',
      locationId: '3',
      serialNumber: '  SN-1234  ',
      description: '  Test asset  ',
      purchasedAt: '2026-01-01',
      warrantyUntil: '2027-01-01',
    })

    expect(httpClient.post).toHaveBeenCalledWith('/assets', {
      assetCode: 'AST-001',
      equipmentTypeId: 2,
      locationId: 3,
      serialNumber: 'SN-1234',
      description: 'Test asset',
      purchasedAt: '2026-01-01',
      warrantyUntil: '2027-01-01',
    })
  })

  it('update sends version, equipmentTypeId, locationId and omits assetCode', async () => {
    httpClient.put.mockResolvedValueOnce({ data: { id: 1, version: 1 } })

    await assetApi.update(1, {
      assetCode: 'SHOULD_NOT_BE_SENT',
      version: 0,
      equipmentTypeId: 2,
      locationId: 3,
      serialNumber: 'SN-999',
      description: 'Updated description',
      purchasedAt: '2026-01-01',
      warrantyUntil: null,
    })

    expect(httpClient.put).toHaveBeenCalledWith('/assets/1', {
      version: 0,
      equipmentTypeId: 2,
      locationId: 3,
      serialNumber: 'SN-999',
      description: 'Updated description',
      purchasedAt: '2026-01-01',
      warrantyUntil: null,
    })
  })

  it('changeStatus sends targetStatus, trimmed reason, and version', async () => {
    httpClient.post.mockResolvedValueOnce({
      data: { id: 1, status: 'UNDER_MAINTENANCE' },
    })

    await assetApi.changeStatus(1, {
      targetStatus: 'UNDER_MAINTENANCE',
      reason: '   Maintenance check   ',
      version: 2,
    })

    expect(httpClient.post).toHaveBeenCalledWith('/assets/1/status', {
      targetStatus: 'UNDER_MAINTENANCE',
      reason: 'Maintenance check',
      version: 2,
    })
  })
})
