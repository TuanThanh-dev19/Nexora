import { useEffect, useState } from 'react'
import { Alert, Button, Form } from 'react-bootstrap'
import {
  IMAGE_TYPES,
  MAX_FILES,
  MAX_FILE_BYTES,
} from '../utils/createTicketContract'

export function EvidenceThumbnail({ file }) {
  const [url, setUrl] = useState('')
  useEffect(() => {
    const next = URL.createObjectURL(file)
    // The URL belongs to this mounted thumbnail and must be released on cleanup.
    // oxlint-disable-next-line react/set-state-in-effect
    setUrl(next)
    return () => URL.revokeObjectURL(next)
  }, [file])
  return url ? (
    <img
      src={url}
      alt={file.name}
      width={96}
      height={72}
      style={{ objectFit: 'cover' }}
    />
  ) : null
}

export function EvidencePicker({
  field,
  photos,
  onChange,
  disabled,
  errors = {},
  error: fieldError,
}) {
  const [error, setError] = useState('')
  const fieldKey = field?.fieldKey
  const id = `evidence-${fieldKey ?? 'general'}`
  const selected = photos.filter((p) => p.fieldKey === fieldKey)
  const rules = field?.validationRules ?? {}
  return (
    <div className="mb-3">
      <Form.Label htmlFor={id}>
        {field?.label ?? 'Ảnh minh chứng chung'}
        {field?.required ? ' *' : ''}
      </Form.Label>
      <Form.Control
        id={id}
        type="file"
        multiple
        accept="image/jpeg,image/png"
        disabled={disabled}
        aria-describedby={`${id}-help ${id}-error`}
        aria-invalid={!!(error || fieldError)}
        onChange={(event) => {
          const files = [...event.target.files]
          event.target.value = ''
          if (
            photos.length + files.length > MAX_FILES ||
            selected.length + files.length > (rules.maxFiles ?? MAX_FILES)
          )
            return setError('Vượt giới hạn số ảnh cho phép.')
          if (
            files.some(
              (f) =>
                !IMAGE_TYPES.includes(f.type) ||
                (rules.allowedTypes && !rules.allowedTypes.includes(f.type)),
            )
          )
            return setError('Chỉ chọn ảnh JPEG/PNG được phép.')
          if (
            files.some(
              (f) =>
                f.size >
                Math.min(MAX_FILE_BYTES, rules.maxSizeBytes ?? Infinity),
            )
          )
            return setError('Ảnh vượt giới hạn dung lượng cho phép.')
          setError('')
          onChange([
            ...photos,
            ...files.map((file) => ({
              id: crypto.randomUUID(),
              file,
              fieldKey,
            })),
          ])
        }}
      />
      <Form.Text id={`${id}-help`}>
        {field?.helpText} JPEG/PNG; tối đa 5 MiB/ảnh, 5 ảnh cho toàn phiếu. Đã
        chọn {photos.length}/5 ảnh.
      </Form.Text>
      {(error || fieldError) && (
        <Alert variant="danger" id={`${id}-error`} role="alert">
          {error || fieldError}
        </Alert>
      )}
      <div className="d-flex flex-wrap gap-3 mt-2">
        {selected.map((p) => (
          <div
            key={p.id}
            className="border rounded p-2"
            style={{ maxWidth: '100%', overflowWrap: 'anywhere' }}
          >
            <EvidenceThumbnail file={p.file} />
            <div>{p.file.name}</div>
            <small>{(p.file.size / 1024).toFixed(1)} KiB</small>
            <Button
              variant="link"
              disabled={disabled}
              aria-label={`Xóa ảnh ${p.file.name}`}
              onClick={() => onChange(photos.filter((x) => x.id !== p.id))}
            >
              Xóa ảnh
            </Button>
            {errors[`attachments[${photos.indexOf(p)}]`] && (
              <p role="alert" className="text-danger">
                {errors[`attachments[${photos.indexOf(p)}]`]}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
