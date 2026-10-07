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
        throw new Error(`Duplicate validation rule: ${key}.`)
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
  fields: form.fields.map((field) => ({
    ...field,
    uiId: crypto.randomUUID(),
    rulesText: JSON.stringify(field.validationRules, null, 2),
    options: field.options.map((option) => ({
      ...option,
      uiId: crypto.randomUUID(),
    })),
  })),
})

export function validateRules(type, rules) {
  if (!rules || Array.isArray(rules) || typeof rules !== 'object')
    return 'Validation rules must be a JSON object.'
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
    if (!allowed.includes(key)) return `${key} is not supported for ${type}.`
    if (key === 'pattern') {
      try {
        if (typeof value !== 'string' || value.length > 500) throw new Error()
        new RegExp(value)
      } catch {
        return 'Use a valid regular expression (at most 500 characters).'
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
        return 'Choose supported image MIME types.'
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
        return `Use an ISO ${type} value.`
    } else if (type === 'NUMBER') {
      if (
        typeof value !== 'number' ||
        !Number.isFinite(value) ||
        (key === 'step' && value <= 0)
      )
        return 'Use a finite number; step must be positive.'
    } else if (
      !Number.isInteger(value) ||
      value < 0 ||
      value > 2147483647 ||
      (['maxFiles', 'maxSizeBytes'].includes(key) && value === 0)
    )
      return 'Use a non-negative integer (file limits must be positive).'
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
      return 'Maximum must be greater than or equal to minimum.'
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
    errors.minAttachments =
      'Minimum attachments must be a non-negative integer.'
  const keys = new Set()
  if (draft.fields.length > 100)
    errors.fields = 'A form may contain at most 100 fields.'
  draft.fields.forEach((field, i) => {
    const p = `fields[${i}]`
    if (!/^[A-Za-z][A-Za-z0-9_]{0,79}$/.test(field.fieldKey))
      errors[`${p}.fieldKey`] =
        'Use a key beginning with a letter, followed by letters, numbers or underscores (max 80).'
    const key = field.fieldKey.toLowerCase()
    if (keys.has(key))
      errors[`${p}.fieldKey`] = 'Field keys must be unique (case insensitive).'
    keys.add(key)
    if (!field.label.trim() || field.label.length > 150)
      errors[`${p}.label`] = 'Enter a label (max 150 characters).'
    if (!DYNAMIC_FIELD_TYPES.includes(field.fieldType))
      errors[`${p}.fieldType`] = 'Choose a supported field type.'
    if ((field.helpText?.length ?? 0) > 500)
      errors[`${p}.helpText`] = 'Help text is limited to 500 characters.'
    if ((field.placeholder?.length ?? 0) > 250)
      errors[`${p}.placeholder`] = 'Placeholder is limited to 250 characters.'
    try {
      const error = validateRules(field.fieldType, parseRules(field.rulesText))
      if (error) errors[`${p}.validationRules`] = error
    } catch (error) {
      errors[`${p}.validationRules`] = error.message.startsWith(
        'Duplicate validation rule:',
      )
        ? error.message
        : 'Enter valid JSON. Rules are never silently removed.'
    }
    if (!isSelect(field.fieldType) && field.options.length)
      errors[`${p}.options`] = 'Only select fields may have options.'
    if (field.options.length > 200)
      errors[`${p}.options`] = 'A field may contain at most 200 options.'
    if (
      context.publishing &&
      isSelect(field.fieldType) &&
      !field.options.some((o) => o.active)
    )
      errors[`${p}.options`] = 'Select fields need at least one active option.'
    const values = new Set()
    field.options.forEach((option, j) => {
      const op = `${p}.options[${j}]`
      if (!/^[A-Za-z0-9_.-]{1,100}$/.test(option.optionValue))
        errors[`${op}.optionValue`] =
          'Enter an option value using letters, numbers, dots, dashes or underscores (max 100).'
      if (values.has(option.optionValue.toLowerCase()))
        errors[`${op}.optionValue`] =
          'Option values must be unique (case insensitive).'
      values.add(option.optionValue.toLowerCase())
      if (!option.optionLabel.trim() || option.optionLabel.length > 150)
        errors[`${op}.optionLabel`] =
          'Enter an option label (max 150 characters).'
    })
  })
  if (context.publishing) {
    if (!context.category?.active)
      errors['category.active'] = 'Activate the category before publishing.'
    if (
      draft.requiresEquipment &&
      context.category?.assetPolicy === 'FORBIDDEN'
    )
      errors.requiresEquipment =
        'Equipment cannot be required when assets are forbidden.'
    if (
      !context.skills?.some(
        (s) => s.id === Number(draft.defaultSkillId) && s.active,
      )
    )
      errors.defaultSkillId = 'Choose an active default skill.'
    if (
      !context.slas?.some(
        (s) => s.id === Number(draft.defaultSlaPolicyId) && s.active,
      )
    )
      errors.defaultSlaPolicyId = 'Choose an active SLA policy.'
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
