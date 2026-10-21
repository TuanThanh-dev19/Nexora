/**
 * Asset Policy Helper & Eligibility Rules
 * Business Policies:
 * - FORBIDDEN: Category does not allow assets. Selector is disabled/hidden, asset is cleared.
 * - REQUIRED: Asset is mandatory (either policy is REQUIRED or form requiresEquipment is true).
 * - OPTIONAL: Asset can be selected or left empty.
 */

export const ASSET_POLICIES = Object.freeze({
  REQUIRED: 'REQUIRED',
  OPTIONAL: 'OPTIONAL',
  FORBIDDEN: 'FORBIDDEN',
})

/**
 * Resolves the effective asset policy given form/category settings.
 * @param {Object} options
 * @param {string} [options.assetPolicy] - 'REQUIRED' | 'OPTIONAL' | 'FORBIDDEN'
 * @param {boolean} [options.requiresEquipment] - Whether equipment is strictly required
 * @param {string} [options.policy] - Direct policy string override if passed as policy
 * @returns {'REQUIRED' | 'OPTIONAL' | 'FORBIDDEN'}
 */
export function resolveAssetPolicy(options = {}) {
  // If a direct string is passed as options or options.policy
  const rawPolicy =
    typeof options === 'string'
      ? options
      : (options.policy ?? options.assetPolicy)

  if (rawPolicy === ASSET_POLICIES.FORBIDDEN) {
    return ASSET_POLICIES.FORBIDDEN
  }

  if (
    rawPolicy === ASSET_POLICIES.REQUIRED ||
    options.requiresEquipment === true
  ) {
    return ASSET_POLICIES.REQUIRED
  }

  if (rawPolicy === ASSET_POLICIES.OPTIONAL) {
    return ASSET_POLICIES.OPTIONAL
  }

  return ASSET_POLICIES.OPTIONAL
}

/**
 * Verifies whether an asset is eligible for the given location:
 * 1. Must belong to the exact location (not parent, not child).
 * 2. Status must be ACTIVE or UNDER_MAINTENANCE.
 *
 * @param {Object} asset
 * @param {number|string} locationId
 * @returns {boolean}
 */
export function isAssetEligible(asset, locationId) {
  if (!asset || locationId == null || locationId === '') return false

  const assetLocationId = asset.location?.id ?? asset.locationId

  if (
    assetLocationId == null ||
    String(assetLocationId) !== String(locationId)
  ) {
    return false
  }

  const eligibleStatuses = ['ACTIVE', 'UNDER_MAINTENANCE']
  return eligibleStatuses.includes(asset.status)
}
