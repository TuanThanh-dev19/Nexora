import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import userEvent from '@testing-library/user-event'
import { FormPreview } from './FormPreview'
import { newField } from '../schemas/formValidation'

it('keeps the editor preview usable while image rules are invalid', () => {
  render(
    <FormPreview
      fields={[
        {
          ...newField(),
          label: 'Photo',
          fieldType: 'IMAGE',
          rulesText: '{"allowedTypes":123}',
        },
      ]}
    />,
  )
  expect(screen.getByText('Xem trước cho người gửi')).toBeInTheDocument()
  expect(screen.getByText('Photo')).toBeInTheDocument()
  expect(screen.getByRole('alert')).toHaveTextContent(
    'Quy tắc gốc được giữ nguyên',
  )
})

it('renders all nine types with active options, help and current ordering', async () => {
  const fields = [
    'TEXT',
    'TEXTAREA',
    'NUMBER',
    'SELECT',
    'MULTI_SELECT',
    'DATE',
    'DATETIME',
    'BOOLEAN',
    'IMAGE',
  ].map((type, i) => ({
    ...newField(),
    uiId: String(i),
    label: type,
    fieldType: type,
    helpText: `Hướng dẫn ${type}`,
    required: true,
    options: ['SELECT', 'MULTI_SELECT'].includes(type)
      ? [
          { optionValue: 'b', optionLabel: 'B', active: true },
          { optionValue: 'hidden', optionLabel: 'Ẩn', active: false },
          { optionValue: 'a', optionLabel: 'A', active: true },
        ]
      : [],
  }))
  render(
    <FormPreview
      fields={[...fields, { ...newField(), label: 'Trường ẩn', active: false }]}
    />,
  )
  expect(screen.getByLabelText('TEXT *')).toHaveAttribute('type', 'text')
  expect(screen.getByLabelText('TEXTAREA *').tagName).toBe('TEXTAREA')
  expect(screen.getByLabelText('NUMBER *')).toHaveAttribute('type', 'number')
  expect(
    [...screen.getByLabelText('SELECT *').options].map((o) => o.textContent),
  ).toEqual(['Chọn giá trị', 'B', 'A'])
  expect(screen.getByLabelText('MULTI_SELECT *')).toHaveAttribute('multiple')
  expect(screen.getByLabelText('DATE *')).toHaveAttribute('type', 'date')
  expect(screen.getByLabelText('DATETIME *')).toHaveAttribute(
    'type',
    'datetime-local',
  )
  expect(screen.getByLabelText('IMAGE *')).toHaveAttribute('type', 'file')
  expect(screen.queryByText('Trường ẩn')).not.toBeInTheDocument()
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('NUMBER *'), '0')
  await user.selectOptions(screen.getByLabelText('BOOLEAN *'), 'false')
  expect(screen.getByLabelText('NUMBER *')).toHaveValue(0)
  expect(screen.getByLabelText('BOOLEAN *')).toHaveValue('false')
})
it('keeps sample input separate from draft and resets incompatible control types', async () => {
  const field = { ...newField(), label: 'Mẫu', fieldKey: 'sample' }
  const view = render(<FormPreview fields={[field]} />)
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Mẫu'), 'sample text')
  expect(field).not.toHaveProperty('value')
  view.rerender(<FormPreview fields={[{ ...field, fieldType: 'IMAGE' }]} />)
  expect(screen.getByLabelText('Mẫu')).toHaveAttribute('type', 'file')
})
