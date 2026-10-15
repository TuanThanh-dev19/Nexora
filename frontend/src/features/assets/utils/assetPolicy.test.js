import { describe, expect, it } from 'vitest'
import {
  ASSET_POLICIES,
  isAssetEligible,
  resolveAssetPolicy,
} from './assetPolicy'

describe('assetPolicy', () => {
  describe('resolveAssetPolicy', () => {
    it('resolves FORBIDDEN when assetPolicy is FORBIDDEN even if requiresEquipment is true', () => {
      expect(
        resolveAssetPolicy({
          assetPolicy: 'FORBIDDEN',
          requiresEquipment: true,
        }),
      ).toBe(ASSET_POLICIES.FORBIDDEN)
    })

    it('resolves FORBIDDEN when direct string FORBIDDEN is passed', () => {
      expect(resolveAssetPolicy('FORBIDDEN')).toBe(ASSET_POLICIES.FORBIDDEN)
    })

    it('resolves REQUIRED when assetPolicy is REQUIRED', () => {
      expect(resolveAssetPolicy({ assetPolicy: 'REQUIRED' })).toBe(
        ASSET_POLICIES.REQUIRED,
      )
    })

    it('resolves REQUIRED when requiresEquipment is true', () => {
      expect(
        resolveAssetPolicy({
          assetPolicy: 'OPTIONAL',
          requiresEquipment: true,
        }),
      ).toBe(ASSET_POLICIES.REQUIRED)
    })

    it('resolves OPTIONAL when assetPolicy is OPTIONAL and requiresEquipment is false', () => {
      expect(
        resolveAssetPolicy({
          assetPolicy: 'OPTIONAL',
          requiresEquipment: false,
        }),
      ).toBe(ASSET_POLICIES.OPTIONAL)
    })

    it('defaults to OPTIONAL when no configuration provided', () => {
      expect(resolveAssetPolicy({})).toBe(ASSET_POLICIES.OPTIONAL)
      expect(resolveAssetPolicy()).toBe(ASSET_POLICIES.OPTIONAL)
    })
  })

  describe('isAssetEligible', () => {
    const activeAsset = {
      id: 1,
      assetCode: 'AST-01',
      status: 'ACTIVE',
      location: { id: 10, code: 'R101', name: 'Room 101' },
    }

    const underMaintenanceAsset = {
      id: 2,
      assetCode: 'AST-02',
      status: 'UNDER_MAINTENANCE',
      location: { id: 10, code: 'R101', name: 'Room 101' },
    }

    const inactiveAsset = {
      id: 3,
      assetCode: 'AST-03',
      status: 'INACTIVE',
      location: { id: 10, code: 'R101', name: 'Room 101' },
    }

    const retiredAsset = {
      id: 4,
      assetCode: 'AST-04',
      status: 'RETIRED',
      location: { id: 10, code: 'R101', name: 'Room 101' },
    }

    it('returns true for ACTIVE or UNDER_MAINTENANCE of exact location', () => {
      expect(isAssetEligible(activeAsset, 10)).toBe(true)
      expect(isAssetEligible(activeAsset, '10')).toBe(true)
      expect(isAssetEligible(underMaintenanceAsset, 10)).toBe(true)
    })

    it('returns false for INACTIVE or RETIRED assets even at same location', () => {
      expect(isAssetEligible(inactiveAsset, 10)).toBe(false)
      expect(isAssetEligible(retiredAsset, 10)).toBe(false)
    })

    it('returns false when asset belongs to a different locationId', () => {
      expect(isAssetEligible(activeAsset, 99)).toBe(false)
      expect(isAssetEligible(underMaintenanceAsset, 5)).toBe(false)
    })

    it('returns false when asset or locationId is missing', () => {
      expect(isAssetEligible(null, 10)).toBe(false)
      expect(isAssetEligible(activeAsset, null)).toBe(false)
      expect(isAssetEligible(activeAsset, '')).toBe(false)
    })
  })
})
