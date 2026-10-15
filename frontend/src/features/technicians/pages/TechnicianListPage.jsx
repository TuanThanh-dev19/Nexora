import { useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Container,
  Stack,
  Table,
} from 'react-bootstrap'
import { Link } from 'react-router'
import { FiArrowUpRight, FiRotateCcw, FiUsers } from 'react-icons/fi'
import { EmptyState } from '../../../shared/components/EmptyState'
import { LoadingSpinner } from '../../../shared/components/LoadingSpinner'
import { UserStatusBadge } from '../../administration/components/UserStatusBadge'
import { TechnicianStatusBadge } from '../components/TechnicianStatusBadge'
import { TechnicianFilters } from '../components/TechnicianFilters'
import { TECHNICIAN_LIST_HELPER_TEXT } from '../constants/technicianConstants'
import { formatTechnicianError } from '../api/technicianError'
import { useTechnicians } from '../hooks/useTechnicians'

export function TechnicianListPage() {
  const [params, setParams] = useState({
    page: 0,
    size: 20,
    query: '',
    available: '',
  })

  const query = useTechnicians(params)

  const handleFilterChange = (updates) => {
    setParams((prev) => ({ ...prev, ...updates }))
  }

  const handleResetFilters = () => {
    setParams({
      page: 0,
      size: 20,
      query: '',
      available: '',
    })
  }

  const data = query.data
  const items = data?.content ?? data?.items ?? []
  const totalElements = data?.totalElements ?? items.length
  const totalPages = data?.totalPages ?? 1
  const currentPage = data?.page ?? params.page

  return (
    <Container className="page-container">
      <div className="page-heading">
        <div>
          <p className="eyebrow">QUẢN LÝ KỸ THUẬT VIÊN</p>
          <h1 className="h2">Kỹ thuật viên</h1>
          <p className="text-secondary mb-0">
            {TECHNICIAN_LIST_HELPER_TEXT}
          </p>
        </div>
        <span className="page-heading-icon">
          <FiUsers aria-hidden="true" />
        </span>
      </div>

      <Card className="ticket-list-card">
        <Card.Body className="ticket-list-body">
          <TechnicianFilters
            query={params.query}
            available={params.available}
            onChange={handleFilterChange}
            onReset={handleResetFilters}
          />

          <div className="ticket-results-heading">
            <h2>Danh sách kỹ thuật viên</h2>
            {!query.isError && data && (
              <span className="result-count">{totalElements} kỹ thuật viên</span>
            )}
          </div>

          {query.isPending ? (
            <LoadingSpinner label="Đang tải danh sách kỹ thuật viên..." />
          ) : query.isError ? (
            <Alert variant="danger" className="my-3">
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
          ) : items.length === 0 ? (
            <EmptyState message="Không có kỹ thuật viên nào phù hợp với bộ lọc." />
          ) : (
            <>
              <Table responsive hover className="align-middle ticket-table mb-4">
                <thead>
                  <tr>
                    <th>Họ và tên</th>
                    <th>Email</th>
                    <th>Trạng thái</th>
                    <th>Sẵn sàng</th>
                    <th>Sức chứa tối đa</th>
                    <th className="text-end">Hành động</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((tech) => (
                    <tr key={tech.technicianId}>
                      <td className="fw-semibold">
                        <Link
                          to={`/technicians/${tech.technicianId}`}
                          className="ticket-number-link text-decoration-none"
                        >
                          {tech.fullName}
                          <FiArrowUpRight aria-hidden="true" className="ms-1" />
                        </Link>
                      </td>
                      <td>{tech.email}</td>
                      <td>
                        <UserStatusBadge status={tech.status} />
                      </td>
                      <td>
                        <TechnicianStatusBadge available={tech.available} />
                      </td>
                      <td>
                        <span className="fw-semibold">{tech.maxActiveTickets}</span>{' '}
                        <span className="text-secondary small">phiếu</span>
                      </td>
                      <td className="text-end">
                        <Button
                          as={Link}
                          to={`/technicians/${tech.technicianId}`}
                          size="sm"
                          variant="outline-primary"
                        >
                          Xem / Cấu hình
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>

              <Stack
                direction="horizontal"
                gap={2}
                className="ticket-pagination justify-content-between flex-wrap"
                aria-label="Phân trang danh sách kỹ thuật viên"
              >
                <span>
                  {totalElements} kỹ thuật viên · Trang {currentPage + 1}/
                  {Math.max(1, totalPages)}
                </span>
                <Stack direction="horizontal" gap={2}>
                  <Button
                    variant="outline-secondary"
                    size="sm"
                    disabled={currentPage === 0}
                    onClick={() => handleFilterChange({ page: currentPage - 1 })}
                  >
                    Trang trước
                  </Button>
                  <Button
                    variant="outline-secondary"
                    size="sm"
                    disabled={currentPage + 1 >= totalPages}
                    onClick={() => handleFilterChange({ page: currentPage + 1 })}
                  >
                    Trang sau
                  </Button>
                </Stack>
              </Stack>
            </>
          )}
        </Card.Body>
      </Card>
    </Container>
  )
}
