import { httpClient } from '../../../shared/api/httpClient'
import { eligibleAssetApi } from './eligibleAssetApi'

export const assetApi = {
  /**
   * Lists assets eligible for ticket reporting at the specified location (Issue #38 / #47).
   */
  async getEligibleAssets(locationId, params = {}, signal) {
    return eligibleAssetApi.listEligible(locationId, params, signal)
  },

  /**
   * Lists assets with pagination, sort, locationId and status filters.
   * Only allowed parameters are included.
   */
  async list(params = {}, signal) {
    const safeParams = {}

    if (params.page != null && params.page !== '') {
      safeParams.page = Number(params.page)
    }
    if (params.size != null && params.size !== '') {
      safeParams.size = Number(params.size)
    }
    if (params.sort != null && params.sort !== '') {
      safeParams.sort = String(params.sort).trim()
    }
    if (params.locationId != null && params.locationId !== '') {
      safeParams.locationId = Number(params.locationId)
    }
    if (params.status != null && params.status !== '') {
      safeParams.status = String(params.status).trim()
    }

    const response = await httpClient.get('/assets', {
      params: safeParams,
      signal,
    })
    return response.data
  },

  /**
   * Retrieves full details of a specific asset.
   */
  async getById(id, signal) {
    const response = await httpClient.get(`/assets/${encodeURIComponent(id)}`, {
      signal,
    })
    return response.data
  },

  /**
   * Retrieves status audit history for a specific asset.
   */
  async getStatusHistory(id, signal) {
    const response = await httpClient.get(
      `/assets/${encodeURIComponent(id)}/status-history`,
      { signal },
    )
    return response.data
  },

  /**
   * Registers a new asset.
   * Fields: assetCode, equipmentTypeId, locationId, serialNumber, description, purchasedAt, warrantyUntil.
   */
  async create(payload) {
    const safePayload = {
      assetCode: payload.assetCode?.trim(),
      equipmentTypeId: Number(payload.equipmentTypeId),
      locationId: Number(payload.locationId),
      serialNumber:
        payload.serialNumber != null && payload.serialNumber.trim() !== ''
          ? payload.serialNumber.trim()
          : null,
      description:
        payload.description != null && payload.description.trim() !== ''
          ? payload.description.trim()
          : null,
      purchasedAt: payload.purchasedAt || null,
      warrantyUntil: payload.warrantyUntil || null,
    }

    const response = await httpClient.post('/assets', safePayload)
    return response.data
  },

  /**
   * Updates an existing asset (excluding status and assetCode).
   * Fields: version, equipmentTypeId, locationId, serialNumber, description, purchasedAt, warrantyUntil.
   */
  async update(id, payload) {
    const safePayload = {
      version: Number(payload.version),
      equipmentTypeId: Number(payload.equipmentTypeId),
      locationId: Number(payload.locationId),
      serialNumber:
        payload.serialNumber != null && payload.serialNumber.trim() !== ''
          ? payload.serialNumber.trim()
          : null,
      description:
        payload.description != null && payload.description.trim() !== ''
          ? payload.description.trim()
          : null,
      purchasedAt: payload.purchasedAt || null,
      warrantyUntil: payload.warrantyUntil || null,
    }

    const response = await httpClient.put(
      `/assets/${encodeURIComponent(id)}`,
      safePayload,
    )
    return response.data
  },

  /**
   * Transitions an asset to a new status.
   * Fields: targetStatus, reason, version.
   */
  async changeStatus(id, payload) {
    const safePayload = {
      targetStatus: payload.targetStatus,
      reason: payload.reason?.trim(),
      version: Number(payload.version),
    }

    const response = await httpClient.post(
      `/assets/${encodeURIComponent(id)}/status`,
      safePayload,
    )
    return response.data
  },
}
