import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
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
  expect(screen.getByText('Requester preview')).toBeInTheDocument()
  expect(screen.getByText('Photo')).toBeInTheDocument()
})
