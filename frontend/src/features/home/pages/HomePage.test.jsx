import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('describes the main Nexora workflow modules', () => {
    render(<HomePage />)

    expect(
      screen.getByRole('heading', { name: 'Nexora' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Ticket workflow')).toBeInTheDocument()
    expect(screen.getByText('Dynamic forms')).toBeInTheDocument()
  })
})
