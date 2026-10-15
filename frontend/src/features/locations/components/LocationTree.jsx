import { FiPlus, FiRotateCcw } from 'react-icons/fi'
import { Alert, Button, Card, Form } from 'react-bootstrap'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { EmptyState } from '../../../shared/components/EmptyState'
import { useLocations } from '../hooks/useLocations'
import { LocationTreeNode } from './LocationTreeNode'

export function LocationTree({
  selectedLocation,
  onSelectLocation,
  onOpenCreate,
  activeFilter,
  onChangeActiveFilter,
  expandedIds,
  onToggleExpand,
}) {
  const rootsQuery = useLocations(null, { active: activeFilter })

  return (
    <Card className="shadow-sm location-tree-panel">
      <Card.Body>
        <div className="d-flex align-items-center justify-content-between mb-3 flex-wrap gap-2">
          <div>
            <h2 className="h5 mb-1">Cây địa điểm</h2>
            <small className="text-secondary">
              Phân cấp: Cơ sở → Tòa nhà → Tầng → Phòng / Khu vực
            </small>
          </div>
          <Button
            size="sm"
            variant="primary"
            onClick={() => onOpenCreate(null)}
            className="d-inline-flex align-items-center gap-1"
          >
            <FiPlus aria-hidden="true" /> Thêm địa điểm
          </Button>
        </div>

        {/* Filter controls */}
        <div className="bg-light p-2 rounded mb-3 border">
          <Form.Check
            type="switch"
            id="active-locations-filter"
            label="Chỉ hiển thị đang hoạt động"
            checked={activeFilter}
            onChange={(e) => onChangeActiveFilter(e.target.checked)}
            className="small fw-semibold text-secondary"
          />
        </div>

        {/* Roots tree state */}
        <div className="location-tree-scroll" tabIndex={0} aria-label="Cây phân cấp địa điểm">
          {rootsQuery.isPending ? (
            <LoadingSpinner label="Đang tải danh sách cơ sở..." />
          ) : rootsQuery.isError ? (
            <Alert variant="danger">
              <p className="mb-2">Không thể tải danh sách cơ sở.</p>
              <Button
                size="sm"
                variant="outline-danger"
                onClick={() => rootsQuery.refetch()}
              >
                <FiRotateCcw aria-hidden="true" /> Thử lại
              </Button>
            </Alert>
          ) : rootsQuery.data?.length === 0 ? (
            <EmptyState message="Không có địa điểm nào phù hợp với bộ lọc." />
          ) : (
            <ul className="location-tree" role="tree" aria-label="Danh sách địa điểm phân cấp">
              {rootsQuery.data?.map((root) => (
                <LocationTreeNode
                  key={root.id}
                  node={root}
                  selectedId={selectedLocation?.id}
                  onSelect={onSelectLocation}
                  activeFilter={activeFilter}
                  expandedIds={expandedIds}
                  onToggleExpand={onToggleExpand}
                />
              ))}
            </ul>
          )}
        </div>
      </Card.Body>
    </Card>
  )
}
