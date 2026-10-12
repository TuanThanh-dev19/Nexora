import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import {
  DYNAMIC_FIELD_TYPES,
  DYNAMIC_FIELD_TYPE_LABELS,
} from '../constants/dynamicFieldTypes'
import { DynamicFieldPreview } from './DynamicFieldPreview'

describe('DynamicFieldPreview', () => {
  it('renders every field type supported by SQL Server', () => {
    render(
      <div>
        {DYNAMIC_FIELD_TYPES.map((type) => (
          <DynamicFieldPreview
            key={type}
            field={{ label: `Field ${type}`, required: false, type }}
          />
        ))}
      </div>,
    )

    for (const type of DYNAMIC_FIELD_TYPES) {
      expect(
        screen.getByText(DYNAMIC_FIELD_TYPE_LABELS[type]),
      ).toBeInTheDocument()
    }
  })
})
