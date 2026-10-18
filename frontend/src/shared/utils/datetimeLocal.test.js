import { expect, it } from 'vitest'
import { datetimeLocal } from './datetimeLocal'

it('renders an offset datetime bound as local time while preserving the instant', () => {
  const bound = '2026-10-18T09:30:00+07:00'
  expect(new Date(datetimeLocal(bound)).toISOString()).toBe(
    '2026-10-18T02:30:00.000Z',
  )
  expect(datetimeLocal()).toBeUndefined()
  expect(datetimeLocal('invalid')).toBeUndefined()
})
