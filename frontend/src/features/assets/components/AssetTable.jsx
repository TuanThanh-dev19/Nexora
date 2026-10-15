import { Button, ButtonGroup, Table } from 'react-bootstrap'
import { Link } from 'react-router'
import { FiArrowUpRight, FiEdit2, FiRefreshCw } from 'react-icons/fi'
import { AssetStatusBadge } from './AssetStatusBadge'

export function AssetTable({ assets, listSearch, onEdit, onChangeStatus }) {
  return (
    <Table responsive hover className="align-middle ticket-table">
      <thead>
        <tr>
          <th>Mã tài sản</th>
          <th>Loại thiết bị</th>
          <th>Địa điểm</th>
          <th>Số sê-ri</th>
          <th>Trạng thái</th>
          <th className="text-end">Thao tác</th>
        </tr>
      </thead>
      <tbody>
        {assets.map((asset) => (
          <tr key={asset.id}>
            <td>
              <Link
                to={`/assets/${asset.id}`}
                state={{ listSearch }}
                className="ticket-number-link fw-semibold"
              >
                {asset.assetCode}
                <FiArrowUpRight aria-hidden="true" />
              </Link>
            </td>
            <td>
              {asset.equipmentType?.name ? (
                <div>
                  <div>{asset.equipmentType.name}</div>
                  <div className="small text-secondary">
                    {asset.equipmentType.code}
                  </div>
                </div>
              ) : (
                '—'
              )}
            </td>
            <td>
              {asset.location?.name ? (
                <div>
                  <div>{asset.location.name}</div>
                  <div className="small text-secondary">
                    {asset.location.code}
                  </div>
                </div>
              ) : (
                '—'
              )}
            </td>
            <td>
              <code>{asset.serialNumber || '—'}</code>
            </td>
            <td>
              <AssetStatusBadge status={asset.status} />
            </td>
            <td className="text-end">
              <ButtonGroup size="sm">
                <Button
                  as={Link}
                  to={`/assets/${asset.id}`}
                  state={{ listSearch }}
                  variant="outline-secondary"
                  title="Xem chi tiết tài sản"
                >
                  Chi tiết
                </Button>
                {onEdit && (
                  <Button
                    variant="outline-primary"
                    onClick={() => onEdit(asset)}
                    title="Chỉnh sửa tài sản"
                  >
                    <FiEdit2 aria-hidden="true" /> Sửa
                  </Button>
                )}
                {onChangeStatus && (
                  <Button
                    variant="outline-warning"
                    onClick={() => onChangeStatus(asset)}
                    title="Đổi trạng thái tài sản"
                  >
                    <FiRefreshCw aria-hidden="true" /> Đổi trạng thái
                  </Button>
                )}
              </ButtonGroup>
            </td>
          </tr>
        ))}
      </tbody>
    </Table>
  )
}
