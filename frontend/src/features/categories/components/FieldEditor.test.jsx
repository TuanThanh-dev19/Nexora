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
    await user.click(screen.getByRole('button', { name: 'Add option' }))
    await user.type(screen.getByLabelText('Option 1 value'), 'a')
    await user.type(screen.getByLabelText('Option 1 label'), 'Alpha')
    await user.click(screen.getByRole('button', { name: 'Add option' }))
    await user.type(screen.getByLabelText('Option 2 value'), 'b')
    await user.type(screen.getByLabelText('Option 2 label'), 'Beta')
    await user.click(screen.getByRole('button', { name: 'Move option 2 up' }))
    expect(screen.getByLabelText('Option 1 value')).toHaveValue('b')
    await user.click(screen.getByRole('button', { name: 'Remove option 2' }))
    expect(screen.queryByLabelText('Option 2 value')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Move field 1 down' }))
    expect(screen.getByText('Field 1: Second')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Remove field 1' }))
    expect(screen.queryByText('Field 1: Second')).not.toBeInTheDocument()
    expect(screen.getByText('Field 1: First')).toBeInTheDocument()
  })

  it('offers options only for select fields and renders backend rule errors inline', async () => {
    const user = userEvent.setup()
    render(<EditorHarness />)
    await user.selectOptions(screen.getAllByLabelText('Field type')[0], 'TEXT')
    expect(
      screen.queryByRole('button', { name: 'Add option' }),
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
      within(container).getByLabelText('Validation rules (JSON)'),
    ).toBeDisabled()
    expect(
      within(container).getByText('Unsupported maximum.'),
    ).toBeInTheDocument()
    expect(
      within(container).queryByRole('button', { name: 'Remove field 1' }),
    ).not.toBeInTheDocument()
  })
})
