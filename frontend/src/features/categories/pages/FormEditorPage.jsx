import { useEffect, useState } from 'react'
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
import { errorMessage } from '../api/categoryError'
import { FieldEditor } from '../components/FieldEditor'
import { FormPreview } from '../components/FormPreview'

export function FormEditorPage() {
  const { formVersionId } = useParams()
  const query = useCategoryForm(formVersionId)
  const skills = useSkills()
  const slas = useSlas()
  const [notice, setNotice] = useState('')
  return (
    <Container fluid="xl" className="page-container">
      <QueryState query={query}>
        {query.data && (
          <FormEditor
            key={`${query.data.id}-${query.data.revision}`}
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

export function FormEditor({ form, skills, slas, notice, setNotice }) {
  const [draft, setDraft] = useState(() => editableForm(form))
  const [dirty, setDirty] = useState(false)
  const [errors, setErrors] = useState({})
  const [confirm, setConfirm] = useState(null)
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
          save: 'Draft saved.',
          publish:
            'Form published. The previous published version has been archived.',
          archive: 'Form archived.',
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
        error.response?.data?.fieldErrors ?? { form: errorMessage(error) },
      )
    },
  })
  useEffect(() => {
    const guard = (event) => {
      if (dirty) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [dirty])
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
    skills.isPending ||
    slas.isPending ||
    skills.isError ||
    slas.isError ||
    Object.keys(publishingErrors).length > 0

  return (
    <>
      <Link
        to={`/categories/${form.category.id}/forms`}
        onClick={(event) => {
          if (dirty && !window.confirm('Discard unsaved changes?'))
            event.preventDefault()
        }}
      >
        ← Form versions
      </Link>
      <div className="d-flex flex-wrap align-items-center gap-3 my-4">
        <h1 className="mb-0">
          {form.category.name} · Version {form.versionNumber}
        </h1>
        <Badge
          bg={readOnly ? 'secondary' : 'warning'}
          text={readOnly ? undefined : 'dark'}
        >
          {form.status}
        </Badge>
        {dirty && (
          <Badge bg="info" text="dark">
            Unsaved changes
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
          This form is read only. Create a new draft from the form versions page
          to make changes.
        </Alert>
      )}
      <FieldErrors errors={errors} />
      {mutation.error?.response?.status === 409 && (
        <Alert variant="warning">
          Your version is stale. Copy any unsaved changes before reloading.{' '}
          <Button
            variant="outline-dark"
            size="sm"
            onClick={() => {
              if (
                !dirty ||
                window.confirm(
                  'Discard unsaved changes and load the latest version?',
                )
              )
                client.invalidateQueries({
                  queryKey: categoryKeys.form(form.id),
                })
            }}
          >
            Reload latest
          </Button>
        </Alert>
      )}
      <Row className="g-4">
        <Col lg={8}>
          <Form onSubmit={save}>
            <fieldset disabled={mutation.isPending}>
              <Card className="mb-4">
                <Card.Header>
                  <strong>Form settings</strong>
                </Card.Header>
                <Card.Body>
                  <fieldset disabled={readOnly}>
                    <Row className="g-3">
                      <Col md={6}>
                        <Form.Check
                          id="requires-equipment"
                          label="Requires equipment"
                          checked={draft.requiresEquipment}
                          onChange={(e) =>
                            update('requiresEquipment', e.target.checked)
                          }
                        />
                        <Form.Text>
                          Category asset policy: {form.category.assetPolicy}
                        </Form.Text>
                      </Col>
                      <Form.Group as={Col} md={6} controlId="min-attachments">
                        <Form.Label>Minimum attachments</Form.Label>
                        <Form.Control
                          type="number"
                          min={0}
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
                        <Form.Label>Default skill</Form.Label>
                        <QueryState query={skills}>
                          <Form.Select
                            value={draft.defaultSkillId}
                            onChange={(e) =>
                              update('defaultSkillId', e.target.value)
                            }
                            isInvalid={!!errors.defaultSkillId}
                          >
                            <option value="">Choose a skill</option>
                            {form.defaultSkill &&
                              !skills.data?.some(
                                (s) => s.id === form.defaultSkill.id,
                              ) && (
                                <option value={form.defaultSkill.id}>
                                  {form.defaultSkill.name} (inactive)
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
                        <Form.Label>Default SLA policy</Form.Label>
                        <QueryState query={slas}>
                          <Form.Select
                            value={draft.defaultSlaPolicyId}
                            onChange={(e) =>
                              update('defaultSlaPolicyId', e.target.value)
                            }
                            isInvalid={!!errors.defaultSlaPolicyId}
                          >
                            <option value="">Choose an SLA policy</option>
                            {form.defaultSlaPolicy &&
                              !slas.data?.some(
                                (s) => s.id === form.defaultSlaPolicy.id,
                              ) && (
                                <option value={form.defaultSlaPolicy.id}>
                                  {form.defaultSlaPolicy.policyName} (inactive)
                                </option>
                              )}
                            {slas.data?.map((sla) => (
                              <option key={sla.id} value={sla.id}>
                                {sla.policyName} · {sla.resolutionMinutes} min
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
              <h2 className="h4">Fields</h2>
              {draft.fields.length === 0 && (
                <Alert variant="light">
                  No fields yet. Add a field to start designing the form.
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
                    Add field
                  </Button>
                  <div className="d-flex flex-wrap gap-2 my-4">
                    <Button type="submit" disabled={!dirty}>
                      {mutation.isPending ? 'Saving…' : 'Save draft'}
                    </Button>
                    <Button
                      variant="success"
                      disabled={publishBlocked}
                      onClick={() => setConfirm('publish')}
                    >
                      Publish form
                    </Button>
                  </div>
                  {dirty && (
                    <p className="text-secondary">
                      Save your changes before publishing.
                    </p>
                  )}
                  {Object.keys(publishingErrors).length > 0 && (
                    <details className="mb-4">
                      <summary>
                        Before publishing (
                        {Object.keys(publishingErrors).length} items)
                      </summary>
                      <ul>
                        {Object.entries(publishingErrors).map(
                          ([path, message]) => (
                            <li key={path}>
                              {path}: {message}
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
                  Archive form
                </Button>
              )}
            </fieldset>
          </Form>
        </Col>
        <Col lg={4}>
          <FormPreview fields={draft.fields} />
        </Col>
      </Row>
      <Modal
        show={!!confirm}
        onHide={() => !mutation.isPending && setConfirm(null)}
        backdrop="static"
      >
        <Modal.Header closeButton={!mutation.isPending}>
          <Modal.Title>
            {confirm === 'publish'
              ? 'Publish this form?'
              : 'Archive this form?'}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {confirm === 'publish'
            ? 'This form will become available for new tickets. Its content will become read only, and the previous published version will be archived.'
            : 'This form will no longer be available for new tickets. Existing ticket references will be preserved.'}
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            disabled={mutation.isPending}
            onClick={() => setConfirm(null)}
          >
            Cancel
          </Button>
          <Button
            variant={confirm === 'publish' ? 'success' : 'danger'}
            disabled={mutation.isPending}
            onClick={() => mutation.mutate(confirm)}
          >
            {mutation.isPending
              ? 'Processing…'
              : confirm === 'publish'
                ? 'Confirm publish'
                : 'Confirm archive'}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  )
}
