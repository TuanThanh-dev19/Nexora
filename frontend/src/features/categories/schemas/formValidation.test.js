import { describe, expect, it } from 'vitest'
import {
  draftPayload,
  editableForm,
  newField,
  parseRules,
  validateDraft,
  validateRules,
} from './formValidation'

describe('dynamic form validation', () => {
  it('rejects a form attachment minimum above the submission limit', () => {
    expect(validateDraft({ minAttachments: 6, fields: [] })).toHaveProperty(
      'minAttachments',
    )
  })
  it('restores server ordering without mutating fields, options or rules', () => {
    const form = {
      revision: 7,
      fields: [
        {
          id: 2,
          displayOrder: 2,
          validationRules: { maxLength: 25 },
          options: [],
        },
        {
          id: 1,
          displayOrder: 1,
          validationRules: {},
          options: [
            { id: 2, displayOrder: 2 },
            { id: 1, displayOrder: 1 },
          ],
        },
      ],
    }
    const copy = structuredClone(form)
    const result = editableForm(form)
    expect(result.fields.map((f) => f.id)).toEqual([1, 2])
    expect(result.fields[0].options.map((o) => o.id)).toEqual([1, 2])
    expect(result.revision).toBe(7)
    expect(JSON.parse(result.fields[1].rulesText)).toEqual({ maxLength: 25 })
    expect(form).toEqual(copy)
  })
  it('rejects duplicate JSON keys including escaped names before serialization', () => {
    expect(() => parseRules('{"maxLength":3,"maxLength":9}')).toThrow(
      /bị trùng/,
    )
    expect(() => parseRules('{"min":1,"\\u006din":2}')).toThrow(/bị trùng/)
    expect(parseRules('{"pattern":"[a-z]+"}')).toEqual({ pattern: '[a-z]+' })
  })
  it.each([
    ['TEXT', []],
    ['TEXT', { unknown: true }],
    ['TEXT', { minLength: 10, maxLength: 2 }],
    ['TEXT', { pattern: '[' }],
    ['NUMBER', { min: '10' }],
    ['NUMBER', { step: 0 }],
    ['BOOLEAN', { min: 1 }],
    ['SELECT', { minItems: 1 }],
    ['DATE', { min: 'yesterday' }],
    ['DATE', { min: '2026-02-30' }],
    ['IMAGE', { allowedTypes: ['text/html'] }],
    ['IMAGE', { maxFiles: 0 }],
    ['IMAGE', { allowedTypes: ['image/gif'] }],
    ['IMAGE', { maxFiles: 6 }],
    ['IMAGE', { maxSizeBytes: 5242881 }],
    ['DATETIME', { min: '2026-10-07T12:30:00' }],
  ])('rejects invalid %s rules: %j', (type, rules) =>
    expect(validateRules(type, rules)).toBeTruthy(),
  )

  it('accepts persisted image rules and numeric bounds', () => {
    expect(
      validateRules('DATETIME', {
        min: '2026-10-07T12:30:00.0+07:00',
        max: '2026-10-07T05:30Z',
      }),
    ).toBeNull()
    expect(
      validateRules('IMAGE', {
        minFiles: 1,
        maxFiles: 3,
        allowedTypes: ['image/jpeg', 'image/png'],
      }),
    ).toBeNull()
    expect(
      validateRules('NUMBER', { min: -1.5, max: 10, step: 0.5 }),
    ).toBeNull()
  })

  it('finds case insensitive duplicate keys, missing active options and asset policy conflicts', () => {
    const field = {
      ...newField(),
      fieldKey: 'Choice',
      label: 'Choice',
      fieldType: 'SELECT',
      options: [],
    }
    const errors = validateDraft(
      {
        minAttachments: -1,
        requiresEquipment: true,
        fields: [field, { ...field, fieldKey: 'choice' }],
      },
      {
        publishing: true,
        category: { active: true, assetPolicy: 'FORBIDDEN' },
        skills: [],
        slas: [],
      },
    )
    expect(errors).toHaveProperty('minAttachments')
    expect(errors['fields[1].fieldKey']).toMatch(/không được trùng/)
    expect(errors['fields[0].options']).toMatch(/lựa chọn hoạt động/)
    expect(errors.requiresEquipment).toMatch(/cấm tài sản/)
    expect(errors.defaultSkillId).toBeTruthy()
    expect(errors.defaultSlaPolicyId).toBeTruthy()
  })

  it('sends rules without dropping properties and serializes current order', () => {
    const first = {
      ...newField(),
      fieldKey: 'second',
      label: 'Second',
      rulesText: '{"maxLength":40,"minLength":2}',
    }
    const second = {
      ...newField(),
      fieldKey: 'first',
      label: 'First',
      fieldType: 'SELECT',
      options: [
        { optionValue: 'b', optionLabel: 'B', active: false },
        { optionValue: 'a', optionLabel: 'A', active: true },
      ],
    }
    const result = draftPayload({
      revision: 4,
      requiresEquipment: false,
      minAttachments: '2',
      defaultSkillId: '1',
      defaultSlaPolicyId: '2',
      fields: [first, second],
    })
    expect(result.fields.map((f) => f.displayOrder)).toEqual([0, 1])
    expect(result.fields[0].validationRules).toEqual({
      maxLength: 40,
      minLength: 2,
    })
    expect(result.fields[1].options.map((o) => o.displayOrder)).toEqual([0, 1])
    expect(result.fields[1].options[0].active).toBe(false)
    expect(result.fields[0]).not.toHaveProperty('uiId')
  })
})
