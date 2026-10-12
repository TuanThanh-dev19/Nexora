import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ASSET_STATUSES, ASSET_STATUS_LABELS } from '../constants/assetStatuses'
import { AssetStatusBadge } from './AssetStatusBadge'

describe('AssetStatusBadge', () => {
  it('renders every canonical asset status', () => {
    render(
      <div>
        {ASSET_STATUSES.map((status) => (
          <AssetStatusBadge key={status} status={status} />
        ))}
      </div>,
    )

    for (const status of ASSET_STATUSES) {
      expect(screen.getByText(ASSET_STATUS_LABELS[status])).toBeInTheDocument()
    }
  })
})
