import { useState } from 'react'
import { Alert, Container } from 'react-bootstrap'
import { LocationTree } from '../components/LocationTree'
import { LocationDetailPanel } from '../components/LocationDetailPanel'
import { LocationModal } from '../components/LocationModal'
import { LocationDeactivateModal } from '../components/LocationDeactivateModal'
import { useLocation } from '../hooks/useLocations'

export function LocationManagementPage() {
  const [selectedLocation, setSelectedLocation] = useState(null)
  const [activeFilter, setActiveFilter] = useState(true)
  const [expandedIds, setExpandedIds] = useState(() => new Set())

  // Alerts
  const [notice, setNotice] = useState(null)

  // Modals state
  const [showLocationModal, setShowLocationModal] = useState(false)
  const [locationToEdit, setLocationToEdit] = useState(null)
  const [initialParent, setInitialParent] = useState(null)

  const [deactivateState, setDeactivateState] = useState({
    show: false,
    location: null,
    targetActive: false,
  })

  // Selected location live query so it stays updated when mutated
  const liveSelectedQuery = useLocation(selectedLocation?.id)
  const currentSelected = liveSelectedQuery.data ?? selectedLocation

  // Toggle expand/collapse of tree node
  const handleToggleExpand = (id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  // Open create modal (with optional initial parent)
  const handleOpenCreate = (parent = null) => {
    setLocationToEdit(null)
    setInitialParent(parent)
    setShowLocationModal(true)
  }

  // Open edit modal
  const handleOpenEdit = (location) => {
    setLocationToEdit(location)
    setInitialParent(null)
    setShowLocationModal(true)
  }

  // Open deactivate / reactivate modal
  const handleOpenDeactivate = (location, targetActive) => {
    setDeactivateState({
      show: true,
      location,
      targetActive,
    })
  }

  // Close location modal
  const handleCloseLocationModal = () => {
    setShowLocationModal(false)
    setLocationToEdit(null)
    setInitialParent(null)
  }

  // Close deactivate modal
  const handleCloseDeactivateModal = () => {
    setDeactivateState({
      show: false,
      location: null,
      targetActive: false,
    })
  }

  // Select location and automatically ensure its parent is expanded
  const handleSelectLocation = (loc) => {
    setSelectedLocation(loc)
    if (loc?.parentId) {
      setExpandedIds((prev) => new Set([...prev, loc.parentId]))
    }
  }

  // Simple breadcrumb: if selected location is set, construct path
  const breadcrumbs = currentSelected ? [currentSelected] : []

  return (
    <Container className="page-container">
      {/* Page Heading */}
      <div className="page-heading">
        <div>
          <h1 className="h2 mb-1">Quản lý địa điểm</h1>
          <p className="text-secondary mb-0">
            Xem và quản lý cấu trúc phân cấp các cơ sở, tòa nhà, tầng và phòng trong khuôn viên.
          </p>
        </div>
      </div>

      {/* Global Success Notification */}
      {notice && (
        <Alert
          variant="success"
          dismissible
          onClose={() => setNotice(null)}
          className="mb-4"
        >
          {notice}
        </Alert>
      )}

      {/* Two-Panel Responsive Management Layout */}
      <div className="location-mgmt-layout">
        {/* Left: Location Tree */}
        <LocationTree
          selectedLocation={currentSelected}
          onSelectLocation={handleSelectLocation}
          onOpenCreate={() => handleOpenCreate(null)}
          activeFilter={activeFilter}
          onChangeActiveFilter={setActiveFilter}
          expandedIds={expandedIds}
          onToggleExpand={handleToggleExpand}
        />

        {/* Right: Selected Location Details */}
        <LocationDetailPanel
          location={currentSelected}
          breadcrumbs={breadcrumbs}
          onSelectLocation={handleSelectLocation}
          onOpenEdit={handleOpenEdit}
          onOpenDeactivate={handleOpenDeactivate}
          onOpenCreateChild={handleOpenCreate}
          activeFilter={activeFilter}
        />
      </div>

      {/* Create / Edit Modal */}
      <LocationModal
        show={showLocationModal}
        onHide={handleCloseLocationModal}
        locationToEdit={locationToEdit}
        initialParent={initialParent}
        onSuccessNotice={(msg) => setNotice(msg)}
      />

      {/* Deactivate / Reactivate Modal */}
      <LocationDeactivateModal
        show={deactivateState.show}
        onHide={handleCloseDeactivateModal}
        location={deactivateState.location}
        targetActive={deactivateState.targetActive}
        onSuccessNotice={(msg) => setNotice(msg)}
      />
    </Container>
  )
}
