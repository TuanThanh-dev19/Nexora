import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Container,
  Form,
  Modal,
  Row,
} from 'react-bootstrap'
import { categoryApi } from '../api/categoryApi'
import {
  categoryKeys,
  useCategoryForm,
  useSkills,
  useSlas,
} from '../hooks/useCategories'
import {
  draftPayload,
  editableForm,
  moveItem,
  newField,
  validateDraft,
} from '../schemas/formValidation'
import { FieldErrors, QueryState } from '../components/CategoryStates'
import {
  errorMessage,
  fieldErrorLabel,
  localizeFieldErrors,
} from '../api/categoryError'
import { FieldEditor } from '../components/FieldEditor'
import { FormPreview } from '../components/FormPreview'
import { UnsavedChangesGuard } from '../components/UnsavedChangesGuard'
import { FORM_STATUS_LABELS } from '../constants/formStatuses'
import { ASSET_POLICY_LABELS } from '../constants/categoryLabels'

export function FormEditorPage() {
  const { formVersionId } = useParams()
  const query = useCategoryForm(formVersionId)
  const skills = useSkills()
  const slas = useSlas()
  const [notice, setNotice] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  return (
    <Container fluid="xl" className="page-container">
      <QueryState query={{ ...query, isError: query.isError && !query.data }}>
        {query.isError && query.data && (
          <Alert variant="danger">
            Không tải được bản mới. Dữ liệu đang chỉnh sửa được giữ nguyên.
          </Alert>
        )}
        {query.data && (
          <FormEditor
            key={`${query.data.id}-${query.data.revision}-${reloadKey}`}
            onReload={async () => {
              const result = await query.refetch()
              if (result.isError) throw result.error
              setReloadKey((n) => n + 1)
            }}
            form={query.data}
            skills={skills}
            slas={slas}
            notice={notice}
            setNotice={setNotice}
          />
        )}
      </QueryState>
    </Container>
  )
}

export function FormEditor({
  form,
  skills,
  slas,
  notice,
  setNotice,
  onReload,
}) {
  const [draft, setDraft] = useState(() => editableForm(form))
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState({})
  const [confirm, setConfirm] = useState(null)
  const [showPreview, setShowPreview] = useState(false)
  const [reloading, setReloading] = useState(false)
  const client = useQueryClient()
  const readOnly = form.status !== 'DRAFT'
  const publishingErrors = validateDraft(draft, {
    publishing: true,
    category: form.category,
    skills: skills.data,
    slas: slas.data,
  })
  const mutation = useMutation({
    mutationFn: (action) =>
      action === 'save'
        ? categoryApi.save(form.id, draftPayload(draft))
        : categoryApi[action](form.id, form.revision),
    onSuccess: async (updated, action) => {
      setConfirm(null)
      setDirty(false)
      setNotice(
        {
          save: 'Đã lưu bản nháp.',
          publish:
            'Đã phát hành biểu mẫu. Phiên bản đã phát hành trước đó được lưu trữ.',
          archive: 'Đã lưu trữ biểu mẫu.',
        }[action],
      )
      client.setQueryData(categoryKeys.form(form.id), updated)
      await client.invalidateQueries({
        queryKey: categoryKeys.versions(form.category.id),
      })
    },
    onError: (error) => {
      setConfirm(null)
      setErrors(
        localizeFieldErrors(
          error.response?.data?.fieldErrors ?? { form: errorMessage(error) },
        ),
      )
    },
  })
  const edit = (next) => {
    setDraft(next)
    setDirty(true)
    setErrors({})
    setNotice('')
  }
  const update = (key, value) => edit({ ...draft, [key]: value })
  const save = (event) => {
    event.preventDefault()
    const next = validateDraft(draft)
    setErrors(next)
    if (!Object.keys(next).length) mutation.mutate('save')
  }
  const publishBlocked =
    dirty ||
    mutation.error?.response?.status === 409 ||
    skills.isPending ||
    slas.isPending ||
    skills.isError ||
    slas.isError ||
    Object.keys(publishingErrors).length > 0

  return (
    <>
      <UnsavedChangesGuard dirty={dirty} />
      <Link to={`/categories/${form.category.id}/forms`}>
        ← Phiên bản biểu mẫu
      </Link>
      <div className="page-heading">
        <h1 className="mb-0">
          {form.category.name} · Phiên bản {form.versionNumber}
        </h1>
        <Badge
          bg={readOnly ? 'secondary' : 'warning'}
          text={readOnly ? undefined : 'dark'}
        >
          {FORM_STATUS_LABELS[form.status] ?? form.status}
        </Badge>
        {dirty && (
          <Badge bg="info" text="dark">
            Thay đổi chưa lưu
          </Badge>
        )}
      </div>
      {notice && (
        <Alert variant="success" role="status">
          {notice}
        </Alert>
      )}
      {readOnly && (
        <Alert variant="info">
          Biểu mẫu này chỉ đọc. Tạo bản nháp mới từ trang phiên bản để chỉnh
          sửa.
        </Alert>
      )}
      <Button
        className="mb-3 d-lg-none"
        variant="outline-primary"
        onClick={() => setShowPreview((v) => !v)}
        aria-expanded={showPreview}
        aria-controls="category-preview"
      >
        {showPreview ? 'Trở lại chỉnh sửa' : 'Xem trước'}
      </Button>
      <FieldErrors errors={errors} />
      {mutation.error?.response?.status === 409 && (
        <Alert variant="warning">
          Phiên bản đang mở đã cũ. Sao chép thay đổi cần giữ trước khi tải bản
          mới.{' '}
          <Button
            variant="outline-dark"
            size="sm"
            onClick={() => setConfirm('reload')}
          >
            Tải bản mới nhất
          </Button>
        </Alert>
      )}
      <Row className="g-4">
        <Col lg={8} className={showPreview ? 'd-none d-lg-block' : undefined}>
          <Form onSubmit={save}>
            <fieldset disabled={mutation.isPending || reloading}>
              <Card className="mb-4">
                <Card.Header>
                  <strong>Cấu hình biểu mẫu</strong>
                </Card.Header>
                <Card.Body>
                  <fieldset disabled={readOnly}>
                    <Row className="g-3">
                      <Col md={6}>
                        <Form.Check
                          id="requires-equipment"
                          label="Bắt buộc tài sản"
                          checked={draft.requiresEquipment}
                          onChange={(e) =>
                            update('requiresEquipment', e.target.checked)
                          }
                        />
                        <Form.Text>
                          Chính sách tài sản của danh mục:{' '}
                          {ASSET_POLICY_LABELS[form.category.assetPolicy]}
                        </Form.Text>
                      </Col>
                      <Form.Group as={Col} md={6} controlId="min-attachments">
                        <Form.Label>Số ảnh tối thiểu</Form.Label>
                        <Form.Control
                          type="number"
                          min={0}
                          max={5}
                          step={1}
                          value={draft.minAttachments}
                          onChange={(e) =>
                            update('minAttachments', e.target.value)
                          }
                          isInvalid={!!errors.minAttachments}
                        />
                        <Form.Control.Feedback type="invalid">
                          {errors.minAttachments}
                        </Form.Control.Feedback>
                      </Form.Group>
                      <Form.Group as={Col} md={6} controlId="default-skill">
                        <Form.Label>Kỹ năng mặc định</Form.Label>
                        <QueryState query={skills}>
                          <Form.Select
                            value={draft.defaultSkillId}
                            onChange={(e) =>
                              update('defaultSkillId', e.target.value)
                            }
                            isInvalid={!!errors.defaultSkillId}
                          >
                            <option value="">Chọn kỹ năng</option>
                            {form.defaultSkill &&
                              !skills.data?.some(
                                (s) => s.id === form.defaultSkill.id,
                              ) && (
                                <option value={form.defaultSkill.id}>
                                  {form.defaultSkill.name} (ngừng hoạt động)
                                </option>
                              )}
                            {skills.data?.map((skill) => (
                              <option key={skill.id} value={skill.id}>
                                {skill.name}
                              </option>
                            ))}
                          </Form.Select>
                        </QueryState>
                        <Form.Control.Feedback type="invalid">
                          {errors.defaultSkillId}
                        </Form.Control.Feedback>
                      </Form.Group>
                      <Form.Group as={Col} md={6} controlId="default-sla">
                        <Form.Label>Chính sách SLA mặc định</Form.Label>
                        <QueryState query={slas}>
                          <Form.Select
                            value={draft.defaultSlaPolicyId}
                            onChange={(e) =>
                              update('defaultSlaPolicyId', e.target.value)
                            }
                            isInvalid={!!errors.defaultSlaPolicyId}
                          >
                            <option value="">Chọn chính sách SLA</option>
                            {form.defaultSlaPolicy &&
                              !slas.data?.some(
                                (s) => s.id === form.defaultSlaPolicy.id,
                              ) && (
                                <option value={form.defaultSlaPolicy.id}>
                                  {form.defaultSlaPolicy.policyName} (ngừng hoạt
                                  động)
                                </option>
                              )}
                            {slas.data?.map((sla) => (
                              <option key={sla.id} value={sla.id}>
                                {sla.policyName} · {sla.resolutionMinutes} phút
                              </option>
                            ))}
                          </Form.Select>
                        </QueryState>
                        <Form.Control.Feedback type="invalid">
                          {errors.defaultSlaPolicyId}
                        </Form.Control.Feedback>
                      </Form.Group>
                    </Row>
                  </fieldset>
                </Card.Body>
              </Card>
              <h2 className="h4">Các trường</h2>
              {draft.fields.length === 0 && (
                <Alert variant="light">
                  Chưa có trường. Thêm trường để bắt đầu thiết kế biểu mẫu.
                </Alert>
              )}
              {draft.fields.map((field, index) => (
                <FieldEditor
                  key={field.uiId}
                  field={field}
                  index={index}
                  count={draft.fields.length}
                  errors={errors}
                  readOnly={readOnly}
                  onChange={(value) =>
                    update(
                      'fields',
                      draft.fields.map((f, i) => (i === index ? value : f)),
                    )
                  }
                  onMove={(offset) =>
                    update('fields', moveItem(draft.fields, index, offset))
                  }
                  onRemove={() =>
                    update(
                      'fields',
                      draft.fields.filter((_, i) => i !== index),
                    )
                  }
                />
              ))}
              {!readOnly && (
                <>
                  <Button
                    variant="outline-primary"
                    disabled={draft.fields.length >= 100}
                    onClick={() =>
                      update('fields', [...draft.fields, newField()])
                    }
                  >
                    Thêm trường
                  </Button>
                  <div className="d-flex flex-wrap gap-2 my-4">
                    <Button
                      type="submit"
                      disabled={
                        !dirty || mutation.error?.response?.status === 409
                      }
                    >
                      {mutation.isPending ? 'Đang lưu…' : 'Lưu bản nháp'}
                    </Button>
                    <Button
                      variant="success"
                      disabled={publishBlocked}
                      onClick={() => setConfirm('publish')}
                    >
                      Phát hành biểu mẫu
                    </Button>
                  </div>
                  {dirty && (
                    <p className="text-secondary">
                      Lưu thay đổi trước khi phát hành.
                    </p>
                  )}
                  {Object.keys(publishingErrors).length > 0 && (
                    <details className="mb-4">
                      <summary>
                        Trước khi phát hành (
                        {Object.keys(publishingErrors).length} mục)
                      </summary>
                      <ul>
                        {Object.entries(publishingErrors).map(
                          ([path, message]) => (
                            <li key={path}>
                              {fieldErrorLabel(path)}: {message}
                            </li>
                          ),
                        )}
                      </ul>
                    </details>
                  )}
                </>
              )}
              {form.status === 'PUBLISHED' && (
                <Button
                  variant="outline-danger"
                  onClick={() => setConfirm('archive')}
                >
                  Lưu trữ biểu mẫu
                </Button>
              )}
            </fieldset>
          </Form>
        </Col>
        <Col lg={4} className={showPreview ? 'd-block' : 'd-none d-lg-block'}>
          <FormPreview fields={draft.fields} />
        </Col>
      </Row>
      <Modal
        show={!!confirm}
        onHide={() => !mutation.isPending && !reloading && setConfirm(null)}
        backdrop="static"
      >
        <Modal.Header closeButton={!mutation.isPending && !reloading}>
          <Modal.Title>
            {confirm === 'reload'
              ? 'Tải phiên bản mới nhất?'
              : confirm === 'publish'
                ? 'Phát hành biểu mẫu này?'
                : 'Lưu trữ biểu mẫu này?'}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {confirm === 'reload'
            ? 'Thay đổi chưa lưu sẽ bị bỏ sau khi tải bản mới thành công. Sao chép nội dung cần giữ trước khi tiếp tục.'
            : confirm === 'publish'
              ? 'Biểu mẫu sẽ được dùng cho phiếu mới và trở thành chỉ đọc. Phiên bản đã phát hành trước đó sẽ được lưu trữ.'
              : 'Biểu mẫu sẽ không còn được dùng cho phiếu mới. Các phiếu đã tham chiếu vẫn được giữ nguyên.'}
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            disabled={mutation.isPending || reloading}
            onClick={() => setConfirm(null)}
          >
            Hủy
          </Button>
          <Button
            variant={confirm === 'publish' ? 'success' : 'danger'}
            disabled={mutation.isPending || reloading}
            onClick={async () => {
              if (confirm !== 'reload') {
                mutation.mutate(confirm)
                return
              }
              setReloading(true)
              try {
                await onReload()
                setConfirm(null)
              } catch {
                setErrors({
                  form: 'Không tải được bản mới. Thay đổi chưa lưu được giữ nguyên.',
                })
                setConfirm(null)
              } finally {
                setReloading(false)
              }
            }}
          >
            {mutation.isPending || reloading
              ? 'Đang xử lý…'
              : confirm === 'reload'
                ? 'Bỏ thay đổi và tải bản mới'
                : confirm === 'publish'
                  ? 'Xác nhận phát hành'
                  : 'Xác nhận lưu trữ'}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  )
}
