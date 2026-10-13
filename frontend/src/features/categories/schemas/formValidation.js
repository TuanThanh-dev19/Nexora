import { DYNAMIC_FIELD_TYPES } from '../constants/dynamicFieldTypes'

export const isSelect = (type) => ['SELECT', 'MULTI_SELECT'].includes(type)

/** JSON.parse alone accepts duplicate keys and silently overwrites earlier values. */
export function parseRules(text) {
  const parsed = JSON.parse(text)
  const tokens = text.match(/"(?:\\.|[^"\\])*"|[{}[\]:,]/g) ?? []
  const stack = []
  tokens.forEach((token, index) => {
    if (token === '{') stack.push(new Set())
    else if (token === '[') stack.push(null)
    else if (token === '}' || token === ']') stack.pop()
    else if (
      token.startsWith('"') &&
      tokens[index + 1] === ':' &&
      stack.at(-1)
    ) {
      const key = JSON.parse(token)
      if (stack.at(-1).has(key))
        throw new Error(`Quy tắc kiểm tra bị trùng: ${key}.`)
      stack.at(-1).add(key)
    }
  })
  return parsed
}
export const newField = () => ({
  uiId: crypto.randomUUID(),
  fieldKey: '',
  label: '',
  fieldType: 'TEXT',
  helpText: '',
  placeholder: '',
  required: false,
  active: true,
  rulesText: '{}',
  options: [],
})
export const newOption = () => ({
  uiId: crypto.randomUUID(),
  optionValue: '',
  optionLabel: '',
  active: true,
})
export const editableForm = (form) => ({
  revision: form.revision,
  requiresEquipment: form.requiresEquipment,
  minAttachments: form.minAttachments,
  defaultSkillId: form.defaultSkillId ?? '',
  defaultSlaPolicyId: form.defaultSlaPolicyId ?? '',
  fields: [...form.fields]
    .sort(
      (a, b) =>
        (a.displayOrder ?? 0) - (b.displayOrder ?? 0) ||
        (a.id ?? 0) - (b.id ?? 0),
    )
    .map((field) => ({
      ...field,
      uiId: crypto.randomUUID(),
      rulesText: JSON.stringify(field.validationRules, null, 2),
      options: [...field.options]
        .sort(
          (a, b) =>
            (a.displayOrder ?? 0) - (b.displayOrder ?? 0) ||
            (a.id ?? 0) - (b.id ?? 0),
        )
        .map((option) => ({
          ...option,
          uiId: crypto.randomUUID(),
        })),
    })),
})

export function validateRules(type, rules) {
  if (!rules || Array.isArray(rules) || typeof rules !== 'object')
    return 'Quy tắc kiểm tra phải là một đối tượng JSON.'
  const allowed =
    {
      TEXT: ['minLength', 'maxLength', 'pattern'],
      TEXTAREA: ['minLength', 'maxLength', 'pattern'],
      NUMBER: ['min', 'max', 'step'],
      DATE: ['min', 'max'],
      DATETIME: ['min', 'max'],
      MULTI_SELECT: ['minItems', 'maxItems'],
      IMAGE: ['minFiles', 'maxFiles', 'maxSizeBytes', 'allowedTypes'],
    }[type] ?? []
  for (const [key, value] of Object.entries(rules)) {
    if (!allowed.includes(key)) return `${key} không được hỗ trợ cho ${type}.`
    if (key === 'pattern') {
      try {
        if (typeof value !== 'string' || value.length > 500) throw new Error()
        new RegExp(value)
      } catch {
        return 'Nhập biểu thức chính quy hợp lệ (tối đa 500 ký tự).'
      }
    } else if (key === 'allowedTypes') {
      if (
        !Array.isArray(value) ||
        !value.length ||
        value.some(
          (v) =>
            !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(v),
        )
      )
        return 'Chọn định dạng MIME ảnh được hỗ trợ.'
    } else if (type === 'DATE' || type === 'DATETIME') {
      const datePattern =
        type === 'DATE'
          ? /^\d{4}-\d{2}-\d{2}$/
          : /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?$/
      if (
        typeof value !== 'string' ||
        !datePattern.test(value) ||
        !Number.isFinite(Date.parse(value)) ||
        new Date(`${value.slice(0, 10)}T00:00:00Z`)
          .toISOString()
          .slice(0, 10) !== value.slice(0, 10)
      )
        return `Nhập giá trị ${type} theo định dạng ISO.`
    } else if (type === 'NUMBER') {
      if (
        typeof value !== 'number' ||
        !Number.isFinite(value) ||
        (key === 'step' && value <= 0)
      )
        return 'Nhập số hữu hạn; bước số phải lớn hơn 0.'
    } else if (
      !Number.isInteger(value) ||
      value < 0 ||
      value > 2147483647 ||
      (['maxFiles', 'maxSizeBytes'].includes(key) && value === 0)
    )
      return 'Nhập số nguyên không âm (giới hạn tệp phải lớn hơn 0).'
  }
  for (const [min, max] of [
    ['minLength', 'maxLength'],
    ['min', 'max'],
    ['minItems', 'maxItems'],
    ['minFiles', 'maxFiles'],
  ]) {
    if (
      rules[min] !== undefined &&
      rules[max] !== undefined &&
      (['DATE', 'DATETIME'].includes(type)
        ? Date.parse(rules[min]) > Date.parse(rules[max])
        : rules[min] > rules[max])
    )
      return 'Giá trị tối đa phải lớn hơn hoặc bằng tối thiểu.'
  }
  return null
}

export function validateDraft(draft, context = {}) {
  const errors = {}
  if (
    draft.minAttachments === '' ||
    !Number.isInteger(Number(draft.minAttachments)) ||
    Number(draft.minAttachments) < 0 ||
    Number(draft.minAttachments) > 2147483647
  )
    errors.minAttachments = 'Số ảnh tối thiểu phải là số nguyên không âm.'
  const keys = new Set()
  if (draft.fields.length > 100)
    errors.fields = 'Biểu mẫu có tối đa 100 trường.'
  draft.fields.forEach((field, i) => {
    const p = `fields[${i}]`
    if (!/^[A-Za-z][A-Za-z0-9_]{0,79}$/.test(field.fieldKey))
      errors[`${p}.fieldKey`] =
        'Mã bắt đầu bằng chữ cái, chỉ gồm chữ, số hoặc gạch dưới (tối đa 80 ký tự).'
    const key = field.fieldKey.toLowerCase()
    if (keys.has(key))
      errors[`${p}.fieldKey`] =
        'Mã trường không được trùng (không phân biệt hoa thường).'
    keys.add(key)
    if (!field.label.trim() || field.label.length > 150)
      errors[`${p}.label`] = 'Nhập nhãn (tối đa 150 ký tự).'
    if (!DYNAMIC_FIELD_TYPES.includes(field.fieldType))
      errors[`${p}.fieldType`] = 'Chọn kiểu trường được hỗ trợ.'
    if ((field.helpText?.length ?? 0) > 500)
      errors[`${p}.helpText`] = 'Hướng dẫn có tối đa 500 ký tự.'
    if ((field.placeholder?.length ?? 0) > 250)
      errors[`${p}.placeholder`] = 'Gợi ý nhập có tối đa 250 ký tự.'
    try {
      const error = validateRules(field.fieldType, parseRules(field.rulesText))
      if (error) errors[`${p}.validationRules`] = error
    } catch (error) {
      errors[`${p}.validationRules`] = error.message.startsWith(
        'Quy tắc kiểm tra bị trùng:',
      )
        ? error.message
        : 'Nhập JSON hợp lệ. Quy tắc lỗi không bị tự động xóa.'
    }
    if (!isSelect(field.fieldType) && field.options.length)
      errors[`${p}.options`] =
        'Chỉ trường chọn một/chọn nhiều được có lựa chọn.'
    if (field.options.length > 200)
      errors[`${p}.options`] = 'Một trường có tối đa 200 lựa chọn.'
    if (
      context.publishing &&
      isSelect(field.fieldType) &&
      !field.options.some((o) => o.active)
    )
      errors[`${p}.options`] = 'Trường chọn cần ít nhất một lựa chọn hoạt động.'
    const values = new Set()
    field.options.forEach((option, j) => {
      const op = `${p}.options[${j}]`
      if (!/^[A-Za-z0-9_.-]{1,100}$/.test(option.optionValue))
        errors[`${op}.optionValue`] =
          'Giá trị lựa chọn chỉ gồm chữ, số, dấu chấm, gạch ngang hoặc gạch dưới (tối đa 100 ký tự).'
      if (values.has(option.optionValue.toLowerCase()))
        errors[`${op}.optionValue`] =
          'Giá trị lựa chọn không được trùng (không phân biệt hoa thường).'
      values.add(option.optionValue.toLowerCase())
      if (!option.optionLabel.trim() || option.optionLabel.length > 150)
        errors[`${op}.optionLabel`] = 'Nhập nhãn lựa chọn (tối đa 150 ký tự).'
    })
  })
  if (context.publishing) {
    if (!context.category?.active)
      errors['category.active'] = 'Kích hoạt danh mục trước khi phát hành.'
    if (
      draft.requiresEquipment &&
      context.category?.assetPolicy === 'FORBIDDEN'
    )
      errors.requiresEquipment =
        'Không thể bắt buộc tài sản khi danh mục cấm tài sản.'
    if (
      !context.skills?.some(
        (s) => s.id === Number(draft.defaultSkillId) && s.active,
      )
    )
      errors.defaultSkillId = 'Chọn kỹ năng mặc định đang hoạt động.'
    if (
      !context.slas?.some(
        (s) => s.id === Number(draft.defaultSlaPolicyId) && s.active,
      )
    )
      errors.defaultSlaPolicyId = 'Chọn chính sách SLA đang hoạt động.'
  }
  return errors
}

export function draftPayload(draft) {
  return {
    revision: draft.revision,
    requiresEquipment: draft.requiresEquipment,
    minAttachments: Number(draft.minAttachments),
    defaultSkillId:
      draft.defaultSkillId === '' ? null : Number(draft.defaultSkillId),
    defaultSlaPolicyId:
      draft.defaultSlaPolicyId === '' ? null : Number(draft.defaultSlaPolicyId),
    fields: draft.fields.map((f, displayOrder) => ({
      fieldKey: f.fieldKey,
      label: f.label,
      fieldType: f.fieldType,
      helpText: f.helpText,
      placeholder: f.placeholder,
      required: f.required,
      active: f.active,
      displayOrder,
      validationRules: parseRules(f.rulesText),
      options: f.options.map((o, order) => ({
        optionValue: o.optionValue,
        optionLabel: o.optionLabel,
        active: o.active,
        displayOrder: order,
      })),
    })),
  }
}

export function moveItem(items, index, offset) {
  const next = [...items]
  const destination = index + offset
  if (destination < 0 || destination >= next.length) return next
  ;[next[index], next[destination]] = [next[destination], next[index]]
  return next
}
