import {
  FiChevronDown,
  FiChevronRight,
  FiGlobe,
  FiHome,
  FiLayers,
  FiMapPin,
  FiRotateCcw,
  FiSquare,
} from 'react-icons/fi'
import { Alert, Badge, Button, Spinner } from 'react-bootstrap'
import {
  LOCATION_TYPE_CODES,
  LOCATION_TYPE_LABELS,
} from '../constants/locationTypes'
import { useLocations } from '../hooks/useLocations'

const TYPE_ICONS = {
  [LOCATION_TYPE_CODES.CAMPUS]: FiGlobe,
  [LOCATION_TYPE_CODES.BUILDING]: FiHome,
  [LOCATION_TYPE_CODES.FLOOR]: FiLayers,
  [LOCATION_TYPE_CODES.ROOM]: FiSquare,
  [LOCATION_TYPE_CODES.AREA]: FiMapPin,
}

const TYPE_BADGE_VARIANTS = {
  [LOCATION_TYPE_CODES.CAMPUS]: 'primary',
  [LOCATION_TYPE_CODES.BUILDING]: 'info',
  [LOCATION_TYPE_CODES.FLOOR]: 'warning',
  [LOCATION_TYPE_CODES.ROOM]: 'success',
  [LOCATION_TYPE_CODES.AREA]: 'secondary',
}

const CAN_HAVE_CHILDREN = [
  LOCATION_TYPE_CODES.CAMPUS,
  LOCATION_TYPE_CODES.BUILDING,
  LOCATION_TYPE_CODES.FLOOR,
]

export function LocationTreeNode({
  node,
  selectedId,
  onSelect,
  activeFilter,
  expandedIds,
  onToggleExpand,
}) {
  const isSelected = selectedId === node.id
  const isExpanded = expandedIds.has(node.id)
  const canExpand = CAN_HAVE_CHILDREN.includes(node.type)
  const Icon = TYPE_ICONS[node.type] ?? FiSquare
  const badgeVariant = TYPE_BADGE_VARIANTS[node.type] ?? 'secondary'
  const typeLabel = LOCATION_TYPE_LABELS[node.type] ?? node.type

  // Lazy query: fetch children only when this node is expanded
  const childrenQuery = useLocations(node.id, {
    active: activeFilter,
    enabled: isExpanded && canExpand,
  })

  const handleToggle = (e) => {
    e.stopPropagation()
    onToggleExpand(node.id)
  }

  const handleSelect = () => {
    onSelect(node)
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      handleSelect()
    } else if (e.key === 'ArrowRight' && canExpand && !isExpanded) {
      e.preventDefault()
      onToggleExpand(node.id)
    } else if (e.key === 'ArrowLeft' && canExpand && isExpanded) {
      e.preventDefault()
      onToggleExpand(node.id)
    }
  }

  return (
    <li className="location-tree-node" role="treeitem" aria-expanded={canExpand ? isExpanded : undefined} aria-selected={isSelected}>
      <div
        className={`location-tree-item ${isSelected ? 'is-selected' : ''}`}
        onClick={handleSelect}
        onKeyDown={handleKeyDown}
        tabIndex={0}
        role="button"
        aria-label={`${node.name} (${node.code}), loại ${typeLabel}`}
      >
        {canExpand ? (
          <button
            type="button"
            className="location-tree-toggle"
            onClick={handleToggle}
            aria-label={isExpanded ? `Thu gọn ${node.name}` : `Mở rộng ${node.name}`}
            tabIndex={-1}
          >
            {childrenQuery.isFetching && isExpanded ? (
              <Spinner animation="border" size="sm" style={{ width: '12px', height: '12px' }} />
            ) : isExpanded ? (
              <FiChevronDown aria-hidden="true" />
            ) : (
              <FiChevronRight aria-hidden="true" />
            )}
          </button>
        ) : (
          <span className="location-tree-toggle-spacer" aria-hidden="true" />
        )}

        <Icon className="text-secondary flex-shrink-0" aria-hidden="true" />

        <span className="flex-grow-1 text-truncate">
          <span>{node.name}</span>{' '}
          <small className="text-secondary font-monospace">({node.code})</small>
        </span>

        <Badge bg={badgeVariant} className="location-type-badge flex-shrink-0">
          {typeLabel}
        </Badge>

        {!node.active && (
          <Badge bg="secondary" className="location-type-badge flex-shrink-0">
            Ngừng
          </Badge>
        )}
      </div>

      {isExpanded && canExpand && (
        <ul className="location-tree-children" role="group">
          {childrenQuery.isPending ? (
            <li className="py-2 px-3 text-secondary small d-flex align-items-center gap-2" role="status">
              <Spinner animation="border" size="sm" />
              <span>Đang tải các địa điểm con...</span>
            </li>
          ) : childrenQuery.isError ? (
            <li className="p-2">
              <Alert variant="warning" className="p-2 mb-0 small">
                <div className="d-flex align-items-center justify-content-between gap-2">
                  <span>Không tải được nhánh này.</span>
                  <Button
                    size="sm"
                    variant="outline-dark"
                    className="py-0 px-2"
                    onClick={() => childrenQuery.refetch()}
                  >
                    <FiRotateCcw aria-hidden="true" /> Thử lại
                  </Button>
                </div>
              </Alert>
            </li>
          ) : childrenQuery.data?.length === 0 ? (
            <li className="py-2 px-3 text-secondary small fst-italic">
              Chưa có địa điểm con nào.
            </li>
          ) : (
            childrenQuery.data?.map((child) => (
              <LocationTreeNode
                key={child.id}
                node={child}
                selectedId={selectedId}
                onSelect={onSelect}
                activeFilter={activeFilter}
                expandedIds={expandedIds}
                onToggleExpand={onToggleExpand}
              />
            ))
          )}
        </ul>
      )}
    </li>
  )
}
