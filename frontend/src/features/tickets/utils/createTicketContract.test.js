import { describe, expect, it } from 'vitest'
import {
  buildTicketMultipart,
  compatibleInput,
  fieldsOf,
  normalizeValue,
  validateSubmission,
} from './createTicketContract'
import { createFormFixture } from '../mocks/createTicketFixture'

const input = {
  categoryId: '1',
  locationId: '2',
  assetId: '',
  title: 'Broken',
  description: 'Details',
  values: {},
}
const photo = (fieldKey) => ({
  id: 'photo',
  fieldKey,
  file: new File(['photo'], 'evidence.png', { type: 'image/png' }),
})
const form = { ...createFormFixture, minAttachments: 0 }
const readBlob = (blob) =>
  new Promise((resolve) => {
    const r = new FileReader()
    r.onload = () => resolve(r.result)
    r.readAsText(blob)
  })

describe('create ticket contract', () => {
  it('requires equipment by either form flag or category policy', () => {
    expect(
      validateSubmission({ ...form, requiresEquipment: true }, input, [])
        .assetId,
    ).toBeTruthy()
    expect(
      validateSubmission(
        { ...form, category: { ...form.category, assetPolicy: 'REQUIRED' } },
        input,
        [],
      ).assetId,
    ).toBeTruthy()
    expect(validateSubmission(form, input, []).assetId).toBeUndefined()
  })
  it('orders active fields and options deterministically', () => {
    expect(
      fieldsOf({
        fields: [
          { id: 3, displayOrder: 1 },
          { id: 2, displayOrder: 1 },
          { id: 1, displayOrder: 0, active: false },
        ],
      }).map((f) => f.id),
    ).toEqual([2, 3])
  })
  it('preserves zero, false, arrays and dates; IMAGE has only attachment mappings', async () => {
    const payload = buildTicketMultipart(
      form,
      {
        ...input,
        values: {
          field0: 'text',
          field1: 'multiline',
          field2: '0',
          field3: 'a',
          field4: ['b', 'a'],
          field5: '2026-10-13',
          field6: '2026-10-13T10:30:00',
          field7: false,
          field8: 'must never send scalar',
        },
      },
      [photo(), photo('field8')],
    )
    const json = JSON.parse(await readBlob(payload.get('ticket')))
    expect(json.dynamicValues).toContainEqual({ fieldKey: 'field2', value: 0 })
    expect(json.dynamicValues).toContainEqual({
      fieldKey: 'field7',
      value: false,
    })
    expect(json.dynamicValues).toContainEqual({
      fieldKey: 'field4',
      value: ['b', 'a'],
    })
    expect(json.dynamicValues).toContainEqual({
      fieldKey: 'field5',
      value: '2026-10-13',
    })
    expect(
      json.dynamicValues.find((f) => f.fieldKey === 'field6').value,
    ).toMatch(/Z$/)
    expect(json.dynamicValues).toHaveLength(8)
    expect(json.attachments).toEqual([
      { fileIndex: 0 },
      { fileIndex: 1, fieldKey: 'field8' },
    ])
    expect(payload.getAll('files')).toHaveLength(2)
    expect(Object.keys(json)).toEqual([
      'categoryId',
      'formVersionId',
      'locationId',
      'title',
      'description',
      'dynamicValues',
      'attachments',
    ])
  })
  it.each([
    'TEXT',
    'TEXTAREA',
    'NUMBER',
    'SELECT',
    'MULTI_SELECT',
    'DATE',
    'DATETIME',
    'BOOLEAN',
    'IMAGE',
  ])('requires %s without rejecting valid false or zero', (type) => {
    const f = {
      ...form,
      fields: [
        {
          fieldKey: 'x',
          fieldType: type,
          required: true,
          active: true,
          validationRules: {},
        },
      ],
    }
    expect(validateSubmission(f, input, [])['dynamicValues.x']).toBeTruthy()
    if (type === 'NUMBER' || type === 'BOOLEAN')
      expect(
        validateSubmission(
          f,
          { ...input, values: { x: type === 'NUMBER' ? 0 : false } },
          [],
        ),
      ).toEqual({})
  })
  it('validates range/step, text pattern/length, selection and dates', () => {
    const fields = [
      {
        fieldKey: 'n',
        fieldType: 'NUMBER',
        validationRules: { min: 1, max: 10, step: 2 },
      },
      {
        fieldKey: 't',
        fieldType: 'TEXT',
        validationRules: { minLength: 2, pattern: '[A-Z]+' },
      },
      {
        fieldKey: 's',
        fieldType: 'SELECT',
        options: [{ optionValue: 'a', active: false }],
      },
      {
        fieldKey: 'm',
        fieldType: 'MULTI_SELECT',
        validationRules: { minItems: 2 },
        options: [{ optionValue: 'a' }],
      },
      {
        fieldKey: 'd',
        fieldType: 'DATE',
        validationRules: { min: '2026-10-13' },
      },
    ]
    const errors = validateSubmission(
      { ...form, fields },
      {
        ...input,
        values: { n: '2', t: 'a', s: 'a', m: ['a'], d: '2026-10-12' },
      },
      [],
    )
    expect(Object.keys(errors)).toHaveLength(5)
  })
  it('enforces evidence global and field limits', () => {
    const f = {
      ...form,
      minAttachments: 1,
      fields: [
        {
          fieldKey: 'image',
          fieldType: 'IMAGE',
          required: true,
          validationRules: { maxFiles: 1 },
        },
      ],
    }
    expect(validateSubmission(f, input, [])).toMatchObject({
      attachments: expect.any(String),
      'dynamicValues.image': expect.any(String),
    })
    expect(
      validateSubmission(f, input, [photo('image'), photo('image')])[
        'dynamicValues.image'
      ],
    ).toBeTruthy()
    expect(
      validateSubmission(form, input, [
        { file: new File(['x'], 'x.gif', { type: 'image/gif' }) },
      ])['attachments[0]'],
    ).toBeTruthy()
  })
  it('keeps only compatible values and field-linked evidence after conflict', () => {
    const next = {
      ...form,
      fields: form.fields
        .filter((f) => !['field0', 'field8'].includes(f.fieldKey))
        .map((f) =>
          f.fieldKey === 'field4'
            ? { ...f, options: f.options.filter((o) => o.optionValue === 'a') }
            : f,
        ),
    }
    const kept = compatibleInput(
      form,
      next,
      { field0: 'removed', field2: 0, field7: false, field4: ['a', 'b'] },
      [photo(), photo('field8')],
    )
    expect(kept.values).toMatchObject({
      field2: 0,
      field7: false,
      field4: ['a'],
    })
    expect(kept.values.field0).toBeUndefined()
    expect(kept.photos).toHaveLength(1)
  })
  it('serializes datetime to a timezone-bearing instant', () => {
    expect(
      normalizeValue({ fieldType: 'DATETIME' }, '2026-10-13T10:30:00+07:00'),
    ).toBe('2026-10-13T03:30:00.000Z')
  })
})
