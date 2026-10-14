import { useState } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { FieldEditor } from './FieldEditor'
import { moveItem, newField } from '../schemas/formValidation'

function EditorHarness() {
  const [fields, setFields] = useState([
    { ...newField(), label: 'First', fieldKey: 'first', fieldType: 'SELECT' },
    { ...newField(), label: 'Second', fieldKey: 'second' },
  ])
  return (
    <>
      {fields.map((field, index) => (
        <FieldEditor
          key={field.uiId}
          field={field}
          index={index}
          count={fields.length}
          onChange={(value) =>
            setFields(fields.map((f, i) => (i === index ? value : f)))
          }
          onMove={(offset) => setFields(moveItem(fields, index, offset))}
          onRemove={() => setFields(fields.filter((_, i) => i !== index))}
        />
      ))}
    </>
  )
}

describe('FieldEditor', () => {
  it('adds, reorders and removes options and fields', async () => {
    const user = userEvent.setup()
    render(<EditorHarness />)
    await user.click(screen.getByRole('button', { name: 'Thêm lựa chọn' }))
    await user.type(screen.getByLabelText('Giá trị lựa chọn 1'), 'a')
    await user.type(screen.getByLabelText('Nhãn lựa chọn 1'), 'Alpha')
    await user.click(screen.getByRole('button', { name: 'Thêm lựa chọn' }))
    await user.type(screen.getByLabelText('Giá trị lựa chọn 2'), 'b')
    await user.type(screen.getByLabelText('Nhãn lựa chọn 2'), 'Beta')
    await user.click(screen.getByRole('button', { name: 'Đưa lựa chọn 2 lên' }))
    expect(screen.getByLabelText('Giá trị lựa chọn 1')).toHaveValue('b')
    await user.click(screen.getByRole('button', { name: 'Xóa lựa chọn 2' }))
    expect(
      screen.queryByLabelText('Giá trị lựa chọn 2'),
    ).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Đưa trường 1 xuống' }))
    expect(screen.getByText('Trường 1: Second')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Xóa trường 1' }))
    expect(screen.queryByText('Trường 1: Second')).not.toBeInTheDocument()
    expect(screen.getByText('Trường 1: First')).toBeInTheDocument()
  })

  it('offers options only for select fields and renders backend rule errors inline', async () => {
    const user = userEvent.setup()
    render(<EditorHarness />)
    await user.selectOptions(screen.getAllByLabelText('Kiểu trường')[0], 'TEXT')
    expect(
      screen.queryByRole('button', { name: 'Thêm lựa chọn' }),
    ).not.toBeInTheDocument()
    const field = { ...newField(), label: 'Locked', fieldKey: 'locked' }
    const { container } = render(
      <FieldEditor
        field={field}
        index={0}
        count={1}
        readOnly
        errors={{
          'fields[0].validationRules.maxLength': 'Unsupported maximum.',
        }}
      />,
    )
    expect(
      within(container).getByLabelText('Quy tắc kiểm tra (JSON)'),
    ).toBeDisabled()
    expect(
      within(container).getByText('Unsupported maximum.'),
    ).toBeInTheDocument()
    expect(
      within(container).queryByRole('button', { name: 'Xóa trường 1' }),
    ).not.toBeInTheDocument()
  })
})
