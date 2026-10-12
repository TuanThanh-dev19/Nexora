import { Badge } from 'react-bootstrap'
import {
  ASSET_STATUS_CODES,
  ASSET_STATUS_LABELS,
} from '../constants/assetStatuses'

const variants = {
  [ASSET_STATUS_CODES.ACTIVE]: 'success',
  [ASSET_STATUS_CODES.UNDER_MAINTENANCE]: 'warning',
  [ASSET_STATUS_CODES.OUT_OF_SERVICE]: 'danger',
  [ASSET_STATUS_CODES.RETIRED]: 'secondary',
}

export function AssetStatusBadge({ status }) {
  const label = ASSET_STATUS_LABELS[status] ?? status ?? 'Unknown'

  return <Badge bg={variants[status] ?? 'secondary'}>{label}</Badge>
}
