import { useState } from 'react'
import { Alert, Badge, Button, Card, Container, Form, Table } from 'react-bootstrap'
import { FiCheckCircle, FiEdit, FiPlus, FiRotateCcw, FiSlash } from 'react-icons/fi'
import { useEquipmentTypes } from '../hooks/useEquipmentTypes'
import { EquipmentTypeModal } from '../components/EquipmentTypeModal'
import { EquipmentTypeDeactivateModal } from '../components/EquipmentTypeDeactivateModal'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { EmptyState } from '../../../shared/components/EmptyState'

export function EquipmentTypeManagementPage() {
  const [activeFilter, setActiveFilter] = useState(true)
  const [notice, setNotice] = useState(null)

  // Modals state
  const [showModal, setShowModal] = useState(false)
  const [editingType, setEditingType] = useState(null)

  const [deactivateState, setDeactivateState] = useState({
    show: false,
    equipmentType: null,
    targetActive: false,
  })

  // Query equipment types with current filter
  const query = useEquipmentTypes(activeFilter)

  const handleOpenCreate = () => {
    setEditingType(null)
    setShowModal(true)
  }

  const handleOpenEdit = (eq) => {
    setEditingType(eq)
    setShowModal(true)
  }

  const handleOpenDeactivate = (eq, targetActive) => {
    setDeactivateState({
      show: true,
      equipmentType: eq,
      targetActive,
    })
  }

  const handleCloseModal = () => {
    setShowModal(false)
    setEditingType(null)
  }

  const handleCloseDeactivate = () => {
    setDeactivateState({
      show: false,
      equipmentType: null,
      targetActive: false,
    })
  }

  return (
    <Container className="page-container">
      {/* Page Heading */}
      <div className="page-heading">
        <div>
          <h1 className="h2 mb-1">Loại thiết bị</h1>
          <p className="text-secondary mb-0">
            Quản lý danh mục loại thiết bị dùng cho quản lý tài sản và báo cáo sự cố.
          </p>
        </div>
        <Button
          variant="primary"
          onClick={handleOpenCreate}
          className="d-inline-flex align-items-center gap-1"
        >
          <FiPlus aria-hidden="true" /> Thêm loại thiết bị
        </Button>
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

      {/* Main Table Card */}
      <Card className="shadow-sm">
        <Card.Body className="p-0">
          {/* Active Filter Bar */}
          <div className="p-3 bg-light border-bottom d-flex align-items-center justify-content-between flex-wrap gap-2">
            <Form.Check
              type="switch"
              id="active-equipment-types-filter"
              label="Chỉ hiển thị loại thiết bị đang hoạt động"
              checked={activeFilter}
              onChange={(e) => setActiveFilter(e.target.checked)}
              className="fw-semibold text-secondary mb-0"
            />
            {query.data && (
              <span className="text-muted small">
                Tổng cộng: <strong>{query.data.length}</strong> loại thiết bị
              </span>
            )}
          </div>

          {/* Table content */}
          {query.isPending ? (
            <div className="p-5">
              <LoadingSpinner label="Đang tải danh sách loại thiết bị..." />
            </div>
          ) : query.isError ? (
            <div className="p-4">
              <Alert variant="danger">
                <p className="mb-2">Không thể tải danh sách loại thiết bị.</p>
                <Button
                  size="sm"
                  variant="outline-danger"
                  onClick={() => query.refetch()}
                >
                  <FiRotateCcw aria-hidden="true" /> Thử lại
                </Button>
              </Alert>
            </div>
          ) : query.data?.length === 0 ? (
            <div className="p-4">
              <EmptyState message="Không có loại thiết bị nào phù hợp với bộ lọc." />
            </div>
          ) : (
            <Table responsive hover className="align-middle mb-0">
              <thead>
                <tr>
                  <th style={{ width: '150px' }}>Mã</th>
                  <th style={{ width: '240px' }}>Tên loại thiết bị</th>
                  <th>Mô tả</th>
                  <th style={{ width: '130px' }}>Trạng thái</th>
                  <th style={{ width: '180px' }} className="text-end">
                    Thao tác
                  </th>
                </tr>
              </thead>
              <tbody>
                {query.data?.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <code>{item.code}</code>
                    </td>
                    <td>
                      <strong>{item.name}</strong>
                    </td>
                    <td className="text-secondary small">
                      {item.description || (
                        <span className="fst-italic text-muted">Chưa có mô tả</span>
                      )}
                    </td>
                    <td>
                      <Badge bg={item.active ? 'success' : 'secondary'}>
                        {item.active ? 'Hoạt động' : 'Ngừng hoạt động'}
                      </Badge>
                    </td>
                    <td className="text-end">
                      <div className="d-inline-flex gap-2">
                        <Button
                          size="sm"
                          variant="outline-secondary"
                          aria-label={`Sửa ${item.name}`}
                          onClick={() => handleOpenEdit(item)}
                        >
                          <FiEdit aria-hidden="true" /> Sửa
                        </Button>
                        {item.active ? (
                          <Button
                            size="sm"
                            variant="outline-danger"
                            aria-label={`Ngừng hoạt động ${item.name}`}
                            onClick={() => handleOpenDeactivate(item, false)}
                          >
                            <FiSlash aria-hidden="true" /> Ngừng
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="outline-success"
                            aria-label={`Kích hoạt lại ${item.name}`}
                            onClick={() => handleOpenDeactivate(item, true)}
                          >
                            <FiCheckCircle aria-hidden="true" /> Bật
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      {/* Create / Edit Modal */}
      <EquipmentTypeModal
        show={showModal}
        onHide={handleCloseModal}
        equipmentTypeToEdit={editingType}
        onSuccessNotice={(msg) => setNotice(msg)}
      />

      {/* Deactivate / Reactivate Modal */}
      <EquipmentTypeDeactivateModal
        show={deactivateState.show}
        onHide={handleCloseDeactivate}
        equipmentType={deactivateState.equipmentType}
        targetActive={deactivateState.targetActive}
        onSuccessNotice={(msg) => setNotice(msg)}
      />
    </Container>
  )
}
