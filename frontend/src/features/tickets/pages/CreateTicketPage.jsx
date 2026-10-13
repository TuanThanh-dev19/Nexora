import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Container, Form, Modal } from 'react-bootstrap'
import { Link, useBlocker, useNavigate } from 'react-router'
import { createTicketApi } from '../api/createTicketApi'
import {
  buildTicketMultipart,
  compatibleInput,
  equipmentRequired,
  fieldsOf,
  validateSubmission,
} from '../utils/createTicketContract'
import { DynamicTicketField } from '../components/DynamicTicketField'
import { EvidencePicker } from '../components/EvidencePicker'
import {
  CreateAssetSelector,
  CreateLocationSelector,
} from '../components/CreateTicketSelectors'

export function CreateTicketPage({
  api = createTicketApi,
  detailBase = '/tickets',
  backPath = '/tickets',
}) {
  const [input, setInput] = useState({
    categoryId: '',
    locationId: '',
    assetId: '',
    title: '',
    description: '',
    values: {},
  })
  const [formOverride, setForm] = useState(null)
  const [location, setLocation] = useState(null)
  const [asset, setAsset] = useState(null)
  const [photos, setPhotos] = useState([])
  const [errors, setErrors] = useState({})
  const [message, setMessage] = useState('')
  const [conflict, setConflict] = useState(false)
  const [pending, setPending] = useState(false)
  const [confirmation, setConfirmation] = useState(null)
  const submitting = useRef(false)
  const saved = useRef(false)
  const summary = useRef(null)
  const navigate = useNavigate()
  const client = useQueryClient()
  const dirty = !!(
    input.title ||
    input.description ||
    Object.keys(input.values).length ||
    photos.length ||
    location ||
    asset
  )
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !saved.current &&
      dirty &&
      currentLocation.pathname !== nextLocation.pathname,
  )
  useEffect(() => {
    const warn = (event) => {
      if (dirty && !saved.current) {
        event.preventDefault()
        event.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
  useEffect(() => {
    if (message || Object.keys(errors).length) summary.current?.focus()
  }, [message, errors])
  const categories = useQuery({
    queryKey: ['create-categories'],
    queryFn: ({ signal }) => api.categories(signal),
  })
  const published = useQuery({
    queryKey: ['create-published-form', input.categoryId],
    queryFn: ({ signal }) => api.form(input.categoryId, signal),
    enabled: !!input.categoryId,
    retry: false,
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  const candidate = formOverride ?? published.data
  const form = candidate?.status === 'PUBLISHED' ? candidate : null
  const change = (key, value) => {
    setInput((old) => ({ ...old, [key]: value }))
    setErrors({})
  }
  const confirm = (action, text) =>
    dirty ? setConfirmation({ action, text }) : action()
  const changeCategory = (id) => {
    if (id === input.categoryId) return
    confirm(() => {
      setInput((old) => ({
        ...old,
        categoryId: id,
        locationId: '',
        assetId: '',
        values: {},
      }))
      setLocation(null)
      setAsset(null)
      setPhotos([])
      setForm(null)
      setErrors({})
      setMessage('')
      setConflict(false)
    }, 'Đổi danh mục sẽ xóa địa điểm, tài sản, dữ liệu biểu mẫu và ảnh đã chọn. Tiêu đề và mô tả được giữ lại.')
  }
  const changeLocation = (next) => {
    if (next?.id === location?.id) return
    const action = () => {
      setLocation(next)
      setAsset(null)
      setInput((old) => ({ ...old, locationId: next?.id ?? '', assetId: '' }))
      setErrors({})
    }
    if (asset)
      setConfirmation({ action, text: 'Đổi địa điểm sẽ xóa tài sản đã chọn.' })
    else action()
  }
  const reloadForm = async () => {
    setPending(true)
    try {
      const next = await api.form(input.categoryId)
      const kept = compatibleInput(form, next, input.values, photos)
      const keepAsset = next.category.assetPolicy !== 'FORBIDDEN'
      setForm(next)
      setPhotos(kept.photos)
      setInput((old) => ({
        ...old,
        values: kept.values,
        assetId: keepAsset ? old.assetId : '',
      }))
      if (!keepAsset) setAsset(null)
      setErrors({})
      setConflict(false)
      setMessage(
        'Đã tải biểu mẫu mới. Kiểm tra lại dữ liệu và ảnh trước khi gửi.',
      )
    } catch {
      setMessage('Không tải được biểu mẫu mới. Dữ liệu đang nhập được giữ lại.')
    } finally {
      setPending(false)
    }
  }
  const submit = async (event) => {
    event.preventDefault()
    if (submitting.current || pending || conflict || published.isFetching)
      return
    const validation = validateSubmission(form, input, photos)
    setErrors(validation)
    setMessage('')
    if (Object.keys(validation).length) return
    submitting.current = true
    setPending(true)
    try {
      const result = await api.submit(buildTicketMultipart(form, input, photos))
      if (!result?.id || !result.ticketNumber)
        throw new Error('Invalid create response')
      saved.current = true
      await client.invalidateQueries({ queryKey: ['tickets'] })
      await client.invalidateQueries({ queryKey: ['dashboard'] })
      navigate(`${detailBase}/${result.id}`, {
        state: { createdTicketNumber: result.ticketNumber },
      })
    } catch (error) {
      const status = error.response?.status
      setErrors(error.response?.data?.fieldErrors ?? {})
      setConflict(status === 409)
      setMessage(
        {
          400: 'Kiểm tra lại các trường được đánh dấu.',
          401: 'Phiên đăng nhập đã hết hạn.',
          403: 'Bạn không có quyền tạo phiếu.',
          404: 'Danh mục, biểu mẫu hoặc dữ liệu tham chiếu không tồn tại; máy chủ có thể chưa hỗ trợ tạo phiếu.',
          409: 'Biểu mẫu đã thay đổi. Tải lại biểu mẫu và kiểm tra dữ liệu tương thích.',
          413: 'Tổng dung lượng ảnh vượt giới hạn máy chủ.',
          415: 'Định dạng ảnh không được máy chủ hỗ trợ.',
          405: 'API tạo phiếu chưa sẵn sàng. Phiếu chưa được lưu.',
        }[status] ?? 'Không gửi được phiếu. Dữ liệu được giữ lại; hãy thử lại.',
      )
    } finally {
      submitting.current = false
      setPending(false)
    }
  }
  const fieldError = (key) =>
    errors[key] && (
      <div role="alert" className="text-danger small" id={`${key}-error`}>
        {errors[key]}
      </div>
    )
  return (
    <Container className="page-container" style={{ maxWidth: 960 }}>
      <Link to={backPath} className="back-link">
        ← Phiếu yêu cầu
      </Link>
      <div className="page-heading">
        <div>
          <p className="eyebrow">BÁO CÁO SỰ CỐ</p>
          <h1 className="h2">Tạo phiếu yêu cầu</h1>
          <p className="text-secondary">
            Điền biểu mẫu đang phát hành để gửi yêu cầu hỗ trợ. Các trường có *
            là bắt buộc.
          </p>
        </div>
      </div>
      {(message || Object.keys(errors).length > 0) && (
        <Alert
          variant={Object.keys(errors).length || conflict ? 'danger' : 'info'}
          role="alert"
          tabIndex={-1}
          ref={summary}
        >
          {message || 'Kiểm tra các trường bên dưới.'}
          <ul>
            {Object.entries(errors).map(([key, value]) => (
              <li key={key}>
                {key}: {String(value)}
              </li>
            ))}
          </ul>
          {conflict && (
            <Button disabled={pending} onClick={reloadForm}>
              Tải lại biểu mẫu
            </Button>
          )}
        </Alert>
      )}
      <Form onSubmit={submit} noValidate>
        <fieldset disabled={pending}>
          <Card className="mb-4">
            <Card.Body>
              <h2 className="h5">1. Thông tin sự cố</h2>
              <Form.Group className="mb-3">
                <Form.Label htmlFor="categoryId">Danh mục *</Form.Label>
                <Form.Select
                  id="categoryId"
                  value={input.categoryId}
                  onChange={(e) => changeCategory(e.target.value)}
                  disabled={categories.isPending || categories.isError}
                  aria-invalid={!!errors.categoryId}
                >
                  <option value="">Chọn danh mục</option>
                  {categories.data
                    ?.filter((c) => c.active)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </Form.Select>
                {fieldError('categoryId')}
              </Form.Group>
              {categories.isPending && (
                <p role="status">Đang tải danh mục...</p>
              )}
              {categories.isError && (
                <Alert variant="danger">
                  Không tải được danh mục.{' '}
                  <Button onClick={() => categories.refetch()}>Thử lại</Button>
                </Alert>
              )}
              {categories.data?.length === 0 && (
                <p>Chưa có danh mục đang hoạt động.</p>
              )}
              {input.categoryId && published.isPending && (
                <p role="status">Đang tải biểu mẫu...</p>
              )}
              {published.isError && (
                <Alert variant="danger">
                  Không tải được biểu mẫu đã phát hành.{' '}
                  <Button onClick={() => published.refetch()}>Thử lại</Button>
                </Alert>
              )}
              {candidate && !form && (
                <Alert variant="danger">
                  Danh mục chưa có biểu mẫu đã phát hành hợp lệ.
                </Alert>
              )}
              {form && (
                <Alert variant="light">
                  Biểu mẫu phiên bản {form.versionNumber} ·{' '}
                  {form.fields?.length ?? 0} trường · Tối thiểu{' '}
                  {form.minAttachments} ảnh
                </Alert>
              )}
              <Form.Group className="mb-3">
                <Form.Label htmlFor="title">Tiêu đề *</Form.Label>
                <Form.Control
                  id="title"
                  value={input.title}
                  onChange={(e) => change('title', e.target.value)}
                  isInvalid={!!errors.title}
                  aria-describedby="title-error"
                />
                {fieldError('title')}
              </Form.Group>
              <Form.Group>
                <Form.Label htmlFor="description">Mô tả *</Form.Label>
                <Form.Control
                  id="description"
                  as="textarea"
                  rows={4}
                  value={input.description}
                  onChange={(e) => change('description', e.target.value)}
                  isInvalid={!!errors.description}
                  aria-describedby="description-error"
                />
                {fieldError('description')}
              </Form.Group>
            </Card.Body>
          </Card>
          {form && (
            <>
              <Card className="mb-4">
                <Card.Body>
                  <h2 className="h5">2. Địa điểm và tài sản</h2>
                  <p>
                    Địa điểm{' '}
                    {form.category.locationRequired
                      ? 'bắt buộc'
                      : 'không bắt buộc'}
                    .{' '}
                    {form.category.assetPolicy === 'FORBIDDEN'
                      ? 'Danh mục không cho phép chọn tài sản.'
                      : equipmentRequired(form)
                        ? 'Bắt buộc chọn tài sản hợp lệ.'
                        : 'Tài sản không bắt buộc.'}
                  </p>
                  <CreateLocationSelector
                    api={api}
                    value={location}
                    onChange={changeLocation}
                    disabled={pending}
                  />
                  {fieldError('locationId')}
                  {form.category.assetPolicy !== 'FORBIDDEN' && (
                    <CreateAssetSelector
                      api={api}
                      key={location?.id ?? 'none'}
                      locationId={location?.id}
                      value={asset}
                      onChange={(next) => {
                        setAsset(next)
                        change('assetId', next?.id ?? '')
                      }}
                      disabled={pending}
                    />
                  )}
                  {fieldError('assetId')}
                </Card.Body>
              </Card>
              <Card className="mb-4">
                <Card.Body>
                  <h2 className="h5">3. Nội dung biểu mẫu</h2>
                  {fieldsOf(form).length === 0 && (
                    <p>Biểu mẫu không có trường bổ sung.</p>
                  )}
                  {fieldsOf(form).map((field) => (
                    <DynamicTicketField
                      key={field.fieldKey}
                      field={field}
                      value={input.values[field.fieldKey]}
                      onChange={(value) =>
                        change('values', {
                          ...input.values,
                          [field.fieldKey]: value,
                        })
                      }
                      error={errors[`dynamicValues.${field.fieldKey}`]}
                      photos={photos}
                      onPhotos={setPhotos}
                      errors={errors}
                      disabled={pending}
                    />
                  ))}
                </Card.Body>
              </Card>
              <Card className="mb-4">
                <Card.Body>
                  <h2 className="h5">4. Ảnh minh chứng</h2>
                  <EvidencePicker
                    photos={photos}
                    onChange={setPhotos}
                    disabled={pending}
                    errors={errors}
                  />
                  {fieldError('attachments')}
                </Card.Body>
              </Card>
            </>
          )}
          <div className="d-flex flex-wrap justify-content-end gap-3 mb-4">
            <Button variant="outline-secondary" as={Link} to={backPath}>
              Hủy / Quay lại
            </Button>
            <Button
              type="submit"
              disabled={!form || published.isFetching || pending || conflict}
            >
              {pending ? 'Đang gửi...' : 'Gửi phiếu'}
            </Button>
          </div>
        </fieldset>
      </Form>
      <Modal
        show={!!confirmation || blocker.state === 'blocked'}
        onHide={() => {
          setConfirmation(null)
          if (blocker.state === 'blocked') blocker.reset()
        }}
        centered
      >
        <Modal.Header closeButton>
          <Modal.Title>Xác nhận thay đổi</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {confirmation?.text ??
            'Bạn có dữ liệu chưa gửi. Rời trang sẽ mất dữ liệu này.'}
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            onClick={() => {
              setConfirmation(null)
              if (blocker.state === 'blocked') blocker.reset()
            }}
          >
            Tiếp tục nhập
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              if (confirmation) {
                confirmation.action()
                setConfirmation(null)
              } else blocker.proceed()
            }}
          >
            Xác nhận
          </Button>
        </Modal.Footer>
      </Modal>
    </Container>
  )
}
