import { expect, it } from 'vitest'
import { formatDate } from './formatDate'

it('uses the common Bangkok timezone and never crashes on missing timestamps', () => {
  expect(formatDate(null)).toBe('—')
  expect(formatDate('invalid')).toBe('—')
  expect(formatDate('2025-12-31T17:30:00Z')).toContain('00:30')
  expect(formatDate('2025-12-31T17:30:00Z')).toContain('2026')
})
