import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import {
  Alert,
  Badge,
  Button,
  Col,
  Container,
  Form,
  Modal,
  Row,
  Table,
} from 'react-bootstrap'
import { categoryApi } from '../api/categoryApi'
import { categoryKeys, useCategories } from '../hooks/useCategories'
import { FieldErrors, QueryState } from '../components/CategoryStates'
import { errorMessage, localizeFieldErrors } from '../api/categoryError'
import { ASSET_POLICY_LABELS } from '../constants/categoryLabels'
import { PRIORITY_LABELS } from '../../../shared/constants/priorities'
import { UnsavedChangesGuard } from '../components/UnsavedChangesGuard'

const emptyCategory = () => ({
  code: '',
  name: '',
  description: '',
  assetPolicy: 'OPTIONAL',
  locationRequired: true,
  defaultPriority: 'NORMAL',
  active: true,
})

export function CategoryListPage() {
  const query = useCategories()
  const client = useQueryClient()
  const [editing, setEditing] = useState(null)
  const [original, setOriginal] = useState(null)
  const [discard, setDiscard] = useState(false)
  const dirty =
    !!editing && JSON.stringify(editing) !== JSON.stringify(original)
  const close = () => {
    if (mutation.isPending) return
    if (dirty) setDiscard(true)
    else setEditing(null)
  }
  const [notice, setNotice] = useState('')
  const [errors, setErrors] = useState({})
  const mutation = useMutation({
    mutationFn: (value) => {
      const { id, ...payload } = value
      return id ? categoryApi.update(id, payload) : categoryApi.create(payload)
    },
    onSuccess: async () => {
      setEditing(null)
      setNotice('Đã lưu danh mục.')
      await client.invalidateQueries({ queryKey: categoryKeys.all })
    },
    onError: (error) =>
      setErrors(
        localizeFieldErrors(
          error.response?.data?.fieldErrors ?? {
            category: errorMessage(error),
          },
        ),
      ),
  })
  const open = (category) => {
    setErrors({})
    mutation.reset()
    setEditing(category)
    setOriginal(category)
    setDiscard(false)
  }
  const update = (key, value) =>
    setEditing((current) => ({ ...current, [key]: value }))

  return (
    <Container className="page-container">
      <UnsavedChangesGuard dirty={dirty} />
      <div className="page-heading">
        <div>
          <h1>Danh mục</h1>
          <p className="text-secondary">
            Quản lý danh mục sự cố và các phiên bản biểu mẫu.
          </p>
        </div>
        <Button onClick={() => open(emptyCategory())}>Tạo danh mục</Button>
      </div>
      {notice && (
        <Alert variant="success" dismissible onClose={() => setNotice('')}>
          {notice}
        </Alert>
      )}
      <QueryState query={query}>
        {query.data?.length === 0 ? (
          <Alert variant="light">
            Chưa có danh mục. Tạo danh mục để thiết kế biểu mẫu đầu tiên.
          </Alert>
        ) : (
          <Table responsive hover className="align-middle">
            <thead>
              <tr>
                <th>Danh mục</th>
                <th>Mã</th>
                <th>Chính sách tài sản</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {query.data?.map((category) => (
                <tr key={category.id}>
                  <td>
                    <strong>{category.name}</strong>
                    <div className="small text-secondary">
                      {category.description}
                    </div>
                  </td>
                  <td>
                    <code>{category.code}</code>
                  </td>
                  <td>{ASSET_POLICY_LABELS[category.assetPolicy]}</td>
                  <td>
                    <Badge bg={category.active ? 'success' : 'secondary'}>
                      {category.active ? 'Hoạt động' : 'Ngừng hoạt động'}
                    </Badge>
                  </td>
                  <td>
                    <div className="d-flex gap-2">
                      <Button
                        as={Link}
                        to={`/categories/${category.id}/forms`}
                        size="sm"
                        variant="outline-primary"
                      >
                        Phiên bản biểu mẫu
                      </Button>
                      <Button
                        size="sm"
                        variant="outline-secondary"
                        aria-label={`Sửa ${category.name}`}
                        onClick={() => open({ ...category })}
                      >
                        Sửa
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </QueryState>
      <Modal show={!!editing} onHide={close} backdrop="static" size="lg">
        <Modal.Header closeButton={!mutation.isPending}>
          <Modal.Title>
            {editing?.id ? 'Sửa danh mục' : 'Tạo danh mục'}
          </Modal.Title>
        </Modal.Header>
        {editing && (
          <Form
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              const validation = {}
              if (!/^[A-Za-z0-9_-]{1,50}$/.test(editing.code))
                validation.code =
                  'Mã gồm chữ, số, gạch ngang hoặc gạch dưới; tối đa 50 ký tự.'
              if (!editing.name.trim() || editing.name.length > 150)
                validation.name = 'Nhập tên danh mục (tối đa 150 ký tự).'
              if ((editing.description?.length ?? 0) > 1000)
                validation.description = 'Mô tả có tối đa 1000 ký tự.'
              setErrors(validation)
              if (Object.keys(validation).length) return
              mutation.mutate(
                Object.fromEntries(
                  ['id', ...Object.keys(emptyCategory())]
                    .filter((key) => editing[key] !== undefined)
                    .map((key) => [key, editing[key]]),
                ),
              )
            }}
          >
            <Modal.Body>
              {discard && (
                <Alert variant="warning" role="alert">
                  Bỏ thay đổi danh mục chưa lưu?{' '}
                  <Button
                    variant="outline-secondary"
                    onClick={() => setDiscard(false)}
                  >
                    Tiếp tục chỉnh sửa
                  </Button>{' '}
                  <Button
                    variant="danger"
                    onClick={() => {
                      setEditing(null)
                      setDiscard(false)
                    }}
                  >
                    Bỏ thay đổi
                  </Button>
                </Alert>
              )}
              <FieldErrors errors={errors} />
              <fieldset disabled={mutation.isPending}>
                <Row className="g-3">
                  <Form.Group as={Col} md={6} controlId="category-code">
                    <Form.Label>Mã</Form.Label>
                    <Form.Control
                      required
                      pattern="[A-Za-z0-9_-]+"
                      maxLength={50}
                      readOnly={!!editing.id}
                      isInvalid={!!errors.code}
                      value={editing.code}
                      onChange={(e) => update('code', e.target.value)}
                    />
                    <Form.Control.Feedback type="invalid">
                      {errors.code}
                    </Form.Control.Feedback>
                    <Form.Text>
                      Mã định danh cố định, không thể thay đổi sau khi tạo.
                    </Form.Text>
                  </Form.Group>
                  <Form.Group as={Col} md={6} controlId="category-name">
                    <Form.Label>Tên</Form.Label>
                    <Form.Control
                      required
                      maxLength={150}
                      isInvalid={!!errors.name}
                      value={editing.name}
                      onChange={(e) => update('name', e.target.value)}
                    />
                    <Form.Control.Feedback type="invalid">
                      {errors.name}
                    </Form.Control.Feedback>
                  </Form.Group>
                  <Form.Group as={Col} xs={12} controlId="category-description">
                    <Form.Label>Mô tả</Form.Label>
                    <Form.Control
                      as="textarea"
                      maxLength={1000}
                      isInvalid={!!errors.description}
                      value={editing.description ?? ''}
                      onChange={(e) => update('description', e.target.value)}
                    />
                    <Form.Control.Feedback type="invalid">
                      {errors.description}
                    </Form.Control.Feedback>
                  </Form.Group>
                  <Form.Group as={Col} md={6} controlId="category-policy">
                    <Form.Label>Chính sách tài sản</Form.Label>
                    <Form.Select
                      isInvalid={!!errors.assetPolicy}
                      value={editing.assetPolicy}
                      onChange={(e) => update('assetPolicy', e.target.value)}
                    >
                      {['OPTIONAL', 'REQUIRED', 'FORBIDDEN'].map((policy) => (
                        <option key={policy} value={policy}>
                          {ASSET_POLICY_LABELS[policy]}
                        </option>
                      ))}
                    </Form.Select>
                    <Form.Control.Feedback type="invalid">
                      {errors.assetPolicy}
                    </Form.Control.Feedback>
                  </Form.Group>
                  <Form.Group as={Col} md={6} controlId="category-priority">
                    <Form.Label>Mức ưu tiên mặc định</Form.Label>
                    <Form.Select
                      isInvalid={!!errors.defaultPriority}
                      value={editing.defaultPriority}
                      onChange={(e) =>
                        update('defaultPriority', e.target.value)
                      }
                    >
                      {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((priority) => (
                        <option key={priority} value={priority}>
                          {PRIORITY_LABELS[priority]}
                        </option>
                      ))}
                    </Form.Select>
                    <Form.Control.Feedback type="invalid">
                      {errors.defaultPriority}
                    </Form.Control.Feedback>
                  </Form.Group>
                  <Col xs={12}>
                    <Form.Check
                      id="category-active"
                      label="Danh mục đang hoạt động"
                      checked={editing.active}
                      onChange={(e) => update('active', e.target.checked)}
                    />
                    <Form.Check
                      id="category-location"
                      label="Bắt buộc địa điểm"
                      checked={editing.locationRequired}
                      onChange={(e) =>
                        update('locationRequired', e.target.checked)
                      }
                    />
                  </Col>
                </Row>
              </fieldset>
            </Modal.Body>
            <Modal.Footer>
              <Button
                variant="secondary"
                disabled={mutation.isPending}
                onClick={close}
              >
                Hủy
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Đang lưu…' : 'Lưu danh mục'}
              </Button>
            </Modal.Footer>
          </Form>
        )}
      </Modal>
    </Container>
  )
}
