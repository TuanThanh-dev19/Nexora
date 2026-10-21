import { useState } from 'react'
import { Alert, Button, Container } from 'react-bootstrap'
import { Link, useParams } from 'react-router'
import { FiEye, FiRotateCcw, FiUserCheck } from 'react-icons/fi'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { ConflictAlert } from '../components/ConflictAlert'
import { TechnicianProfilePanel } from '../components/TechnicianProfilePanel'
import { TechnicianSkillsPanel } from '../components/TechnicianSkillsPanel'
import { TechnicianServiceAreasPanel } from '../components/TechnicianServiceAreasPanel'
import { formatTechnicianError } from '../api/technicianError'
import { useTechnician } from '../hooks/useTechnicians'
import { useAuth } from '../../auth/context/useAuth'

export function TechnicianDetailPage({ readOnly: readOnlyProp }) {
  const { technicianId } = useParams()
  const { user } = useAuth()
  const [conflict, setConflict] = useState(false)

  const isMeRoute = !technicianId || technicianId === 'me'
  const effectiveId = isMeRoute ? 'me' : technicianId

  // Read-only if explicitly provided as prop, if navigating to /technicians/me,
  // or if user is a technician without manager/admin roles.
  const isReadOnly =
    readOnlyProp ??
    (isMeRoute ||
      (user?.roles?.includes('TECHNICIAN') &&
        !user?.roles?.includes('MANAGER') &&
        !user?.roles?.includes('ADMIN')))

  const query = useTechnician(effectiveId)
  const technician = query.data

  const handleReload = () => {
    setConflict(false)
    query.refetch()
  }

  const backLink = isReadOnly ? (
    <Link to="/dashboard" className="back-link">
      ← Tổng quan
    </Link>
  ) : (
    <Link to="/technicians" className="back-link">
      ← Danh sách kỹ thuật viên
    </Link>
  )

  const pageTitle = isMeRoute
    ? 'Hồ sơ kỹ thuật của tôi'
    : technician?.fullName
      ? `Hồ sơ: ${technician.fullName}`
      : 'Hồ sơ kỹ thuật viên'

  return (
    <Container className="page-container">
      {backLink}

      <div className="page-heading">
        <div>
          <p className="eyebrow">
            {isReadOnly ? 'HỒ SƠ CỦA TÔI' : 'CẤU HÌNH KỸ THUẬT VIÊN'}
          </p>
          <h1 className="h2 mb-0">{pageTitle}</h1>
        </div>
        <div className="d-flex align-items-center gap-2">
          {isReadOnly ? (
            <span className="read-only-label">
              <FiEye aria-hidden="true" /> Chế độ chỉ đọc
            </span>
          ) : (
            <span className="page-heading-icon">
              <FiUserCheck aria-hidden="true" />
            </span>
          )}
        </div>
      </div>

      {conflict && <ConflictAlert onReload={handleReload} />}

      {query.isPending ? (
        <LoadingSpinner label="Đang tải thông tin hồ sơ kỹ thuật viên..." />
      ) : query.isError ? (
        <Alert variant="danger" className="my-4">
          <p className="mb-2">{formatTechnicianError(query.error)}</p>
          <Button
            variant="outline-danger"
            size="sm"
            onClick={() => query.refetch()}
            className="d-inline-flex align-items-center gap-1"
          >
            <FiRotateCcw aria-hidden="true" /> Thử lại
          </Button>
        </Alert>
      ) : (
        technician && (
          <div className="technician-detail-sections">
            {/* Panel 1: Profile & Capacity */}
            <TechnicianProfilePanel
              key={`profile-${technician.technicianId}-${technician.version}`}
              technician={technician}
              readOnly={isReadOnly}
              onConflict={() => setConflict(true)}
            />

            {/* Panel 2: Skills */}
            <TechnicianSkillsPanel
              key={`skills-${technician.technicianId}-${technician.version}`}
              technician={technician}
              readOnly={isReadOnly}
              onConflict={() => setConflict(true)}
            />

            {/* Panel 3: Service Areas */}
            <TechnicianServiceAreasPanel
              key={`areas-${technician.technicianId}-${technician.version}`}
              technician={technician}
              readOnly={isReadOnly}
              onConflict={() => setConflict(true)}
            />
          </div>
        )
      )}
    </Container>
  )
}
