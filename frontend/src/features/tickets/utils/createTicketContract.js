export const MAX_FILES = 5
export const MAX_FILE_BYTES = 5 * 1024 * 1024
export const IMAGE_TYPES = ['image/jpeg', 'image/png']
export const orderedActive = (items = []) =>
  items
    .filter((x) => x.active !== false)
    .toSorted((a, b) => a.displayOrder - b.displayOrder || a.id - b.id)
export const fieldsOf = (form) => orderedActive(form?.fields)
export const emptyValue = (value) =>
  value == null || value === '' || (Array.isArray(value) && !value.length)
export const equipmentRequired = (form) =>
  form?.requiresEquipment || form?.category?.assetPolicy === 'REQUIRED'

export function normalizeValue(field, value) {
  if (field.fieldType === 'NUMBER') return Number(value)
  if (field.fieldType === 'BOOLEAN') return value === true || value === 'true'
  if (field.fieldType === 'DATETIME') {
    // datetime-local is user-local; submission contract uses an ISO instant with offset.
    return new Date(value).toISOString()
  }
  return value
}

export function validateSubmission(form, input, photos) {
  const errors = {}
  if (!form || form.status !== 'PUBLISHED')
    errors.categoryId = 'Chọn danh mục có biểu mẫu đã phát hành.'
  if (!input.title.trim()) errors.title = 'Nhập tiêu đề sự cố.'
  if (!input.description.trim()) errors.description = 'Mô tả sự cố.'
  if (form?.category?.locationRequired && !input.locationId)
    errors.locationId = 'Chọn địa điểm.'
  if (equipmentRequired(form) && !input.assetId)
    errors.assetId = 'Chọn tài sản hợp lệ.'
  if (photos.length < (form?.minAttachments ?? 0))
    errors.attachments = `Cần ít nhất ${form.minAttachments} ảnh minh chứng.`
  if (photos.length > MAX_FILES) errors.attachments = `Tối đa ${MAX_FILES} ảnh.`
  for (const field of fieldsOf(form)) {
    const key = `dynamicValues.${field.fieldKey}`
    const value = input.values[field.fieldKey]
    const rules = field.validationRules ?? {}
    if (field.fieldType === 'IMAGE') {
      const count = photos.filter((p) => p.fieldKey === field.fieldKey).length
      if (count < Math.max(field.required ? 1 : 0, rules.minFiles ?? 0))
        errors[key] = 'Chưa đủ ảnh cho trường này.'
      if (count > (rules.maxFiles ?? MAX_FILES))
        errors[key] = 'Vượt số ảnh cho phép của trường.'
      continue
    }
    if (emptyValue(value)) {
      if (field.required) errors[key] = 'Trường này bắt buộc.'
      continue
    }
    switch (field.fieldType) {
      case 'TEXT':
      case 'TEXTAREA': {
        if (
          value.length < (rules.minLength ?? 0) ||
          value.length > (rules.maxLength ?? Infinity)
        )
          errors[key] = 'Độ dài không hợp lệ.'
        if (rules.pattern) {
          try {
            if (!new RegExp(`^(?:${rules.pattern})$`).test(value))
              errors[key] = 'Nội dung không đúng định dạng.'
          } catch {
            errors[key] =
              'Biểu mẫu có quy tắc không tương thích. Liên hệ quản trị viên.'
          }
        }
        break
      }
      case 'NUMBER': {
        const n = Number(value)
        if (
          !Number.isFinite(n) ||
          n < (rules.min ?? -Infinity) ||
          n > (rules.max ?? Infinity)
        )
          errors[key] = 'Số ngoài khoảng cho phép.'
        if (
          rules.step &&
          Math.abs(
            (n - (rules.min ?? 0)) / rules.step -
              Math.round((n - (rules.min ?? 0)) / rules.step),
          ) > 1e-8
        )
          errors[key] = 'Số không đúng bước quy định.'
        break
      }
      case 'SELECT':
      case 'MULTI_SELECT': {
        const selected = Array.isArray(value) ? value : [value]
        const allowed = orderedActive(field.options).map((o) => o.optionValue)
        if (
          selected.some((v) => !allowed.includes(v)) ||
          new Set(selected).size !== selected.length
        )
          errors[key] = 'Lựa chọn không còn hợp lệ.'
        if (
          selected.length < (rules.minItems ?? 0) ||
          selected.length > (rules.maxItems ?? Infinity)
        )
          errors[key] = 'Số lựa chọn không hợp lệ.'
        break
      }
      case 'DATE':
      case 'DATETIME': {
        const comparable =
          field.fieldType === 'DATE' ? value : new Date(value).getTime()
        const bound = (v) =>
          field.fieldType === 'DATE' ? v : new Date(v).getTime()
        if (
          !Number.isFinite(new Date(value).getTime()) ||
          (rules.min && comparable < bound(rules.min)) ||
          (rules.max && comparable > bound(rules.max))
        )
          errors[key] = 'Ngày/giờ ngoài khoảng cho phép.'
        break
      }
    }
  }
  photos.forEach((p, index) => {
    const rules =
      fieldsOf(form).find((f) => f.fieldKey === p.fieldKey)?.validationRules ??
      {}
    if (
      !IMAGE_TYPES.includes(p.file.type) ||
      (rules.allowedTypes && !rules.allowedTypes.includes(p.file.type))
    )
      errors[`attachments[${index}]`] =
        'Chỉ nhận ảnh JPEG/PNG được biểu mẫu cho phép.'
    if (p.file.size > Math.min(MAX_FILE_BYTES, rules.maxSizeBytes ?? Infinity))
      errors[`attachments[${index}]`] = 'Ảnh vượt giới hạn dung lượng.'
  })
  return errors
}

export function buildTicketMultipart(form, input, photos) {
  const ticket = {
    categoryId: Number(input.categoryId),
    formVersionId: form.id,
    locationId: input.locationId ? Number(input.locationId) : null,
    ...(input.assetId && form.category.assetPolicy !== 'FORBIDDEN'
      ? { assetId: Number(input.assetId) }
      : {}),
    title: input.title.trim(),
    description: input.description.trim(),
    dynamicValues: fieldsOf(form)
      .filter(
        (f) => f.fieldType !== 'IMAGE' && !emptyValue(input.values[f.fieldKey]),
      )
      .map((f) => ({
        fieldKey: f.fieldKey,
        value: normalizeValue(f, input.values[f.fieldKey]),
      })),
    attachments: photos.map((p, fileIndex) => ({
      fileIndex,
      ...(p.fieldKey ? { fieldKey: p.fieldKey } : {}),
    })),
  }
  const data = new FormData()
  data.append(
    'ticket',
    new Blob([JSON.stringify(ticket)], { type: 'application/json' }),
  )
  photos.forEach((p) => data.append('files', p.file))
  return data
}

export function compatibleInput(oldForm, nextForm, values, photos) {
  const compatible = fieldsOf(nextForm).filter((f) =>
    fieldsOf(oldForm).some(
      (o) => o.fieldKey === f.fieldKey && o.fieldType === f.fieldType,
    ),
  )
  return {
    values: Object.fromEntries(
      compatible
        .filter((f) => f.fieldType !== 'IMAGE')
        .map((f) => {
          let value = values[f.fieldKey]
          if (['SELECT', 'MULTI_SELECT'].includes(f.fieldType)) {
            const allowed = orderedActive(f.options).map((o) => o.optionValue)
            value = Array.isArray(value)
              ? value.filter((v) => allowed.includes(v))
              : allowed.includes(value)
                ? value
                : ''
          }
          return [f.fieldKey, value]
        }),
    ),
    photos: photos.filter(
      (p) =>
        !p.fieldKey ||
        compatible.some(
          (f) => f.fieldType === 'IMAGE' && f.fieldKey === p.fieldKey,
        ),
    ),
  }
}
