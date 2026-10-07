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
import { errorMessage } from '../api/categoryError'

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
  const [notice, setNotice] = useState('')
  const [errors, setErrors] = useState({})
  const mutation = useMutation({
    mutationFn: (value) =>
      value.id
        ? categoryApi.update(value.id, value)
        : categoryApi.create(value),
    onSuccess: async () => {
      setEditing(null)
      setNotice('Category saved.')
      await client.invalidateQueries({ queryKey: categoryKeys.all })
    },
    onError: (error) =>
      setErrors(
        error.response?.data?.fieldErrors ?? { category: errorMessage(error) },
      ),
  })
  const open = (category) => {
    setErrors({})
    mutation.reset()
    setEditing(category)
  }
  const update = (key, value) =>
    setEditing((current) => ({ ...current, [key]: value }))

  return (
    <Container className="page-container">
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
        <div>
          <h1>Categories</h1>
          <p className="text-secondary">
            Manage incident categories and their versioned forms.
          </p>
        </div>
        <Button onClick={() => open(emptyCategory())}>New category</Button>
      </div>
      {notice && (
        <Alert variant="success" dismissible onClose={() => setNotice('')}>
          {notice}
        </Alert>
      )}
      <QueryState query={query}>
        {query.data?.length === 0 ? (
          <Alert variant="light">
            No categories yet. Create a category to design its first form.
          </Alert>
        ) : (
          <Table responsive hover className="align-middle">
            <thead>
              <tr>
                <th>Category</th>
                <th>Code</th>
                <th>Asset policy</th>
                <th>Status</th>
                <th>Actions</th>
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
                  <td>{category.assetPolicy}</td>
                  <td>
                    <Badge bg={category.active ? 'success' : 'secondary'}>
                      {category.active ? 'Active' : 'Inactive'}
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
                        Form versions
                      </Button>
                      <Button
                        size="sm"
                        variant="outline-secondary"
                        aria-label={`Edit ${category.name}`}
                        onClick={() => open({ ...category })}
                      >
                        Edit
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </QueryState>
      <Modal
        show={!!editing}
        onHide={() => !mutation.isPending && setEditing(null)}
        backdrop="static"
        size="lg"
      >
        <Modal.Header closeButton={!mutation.isPending}>
          <Modal.Title>
            {editing?.id ? 'Edit category' : 'New category'}
          </Modal.Title>
        </Modal.Header>
        {editing && (
          <Form
            onSubmit={(event) => {
              event.preventDefault()
              setErrors({})
              mutation.mutate(editing)
            }}
          >
            <Modal.Body>
              <FieldErrors errors={errors} />
              <fieldset disabled={mutation.isPending}>
                <Row className="g-3">
                  <Form.Group as={Col} md={6} controlId="category-code">
                    <Form.Label>Code</Form.Label>
                    <Form.Control
                      required
                      pattern="[A-Za-z0-9_-]+"
                      maxLength={50}
                      readOnly={!!editing.id}
                      value={editing.code}
                      onChange={(e) => update('code', e.target.value)}
                    />
                    <Form.Text>
                      Stable identifier; cannot change after creation.
                    </Form.Text>
                  </Form.Group>
                  <Form.Group as={Col} md={6} controlId="category-name">
                    <Form.Label>Name</Form.Label>
                    <Form.Control
                      required
                      maxLength={150}
                      value={editing.name}
                      onChange={(e) => update('name', e.target.value)}
                    />
                  </Form.Group>
                  <Form.Group as={Col} xs={12} controlId="category-description">
                    <Form.Label>Description</Form.Label>
                    <Form.Control
                      as="textarea"
                      maxLength={1000}
                      value={editing.description ?? ''}
                      onChange={(e) => update('description', e.target.value)}
                    />
                  </Form.Group>
                  <Form.Group as={Col} md={6} controlId="category-policy">
                    <Form.Label>Asset policy</Form.Label>
                    <Form.Select
                      value={editing.assetPolicy}
                      onChange={(e) => update('assetPolicy', e.target.value)}
                    >
                      {['OPTIONAL', 'REQUIRED', 'FORBIDDEN'].map((policy) => (
                        <option key={policy}>{policy}</option>
                      ))}
                    </Form.Select>
                  </Form.Group>
                  <Form.Group as={Col} md={6} controlId="category-priority">
                    <Form.Label>Default priority</Form.Label>
                    <Form.Select
                      value={editing.defaultPriority}
                      onChange={(e) =>
                        update('defaultPriority', e.target.value)
                      }
                    >
                      {['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((priority) => (
                        <option key={priority}>{priority}</option>
                      ))}
                    </Form.Select>
                  </Form.Group>
                  <Col xs={12}>
                    <Form.Check
                      id="category-active"
                      label="Active category"
                      checked={editing.active}
                      onChange={(e) => update('active', e.target.checked)}
                    />
                    <Form.Check
                      id="category-location"
                      label="Location required"
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
                onClick={() => setEditing(null)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Saving…' : 'Save category'}
              </Button>
            </Modal.Footer>
          </Form>
        )}
      </Modal>
    </Container>
  )
}
