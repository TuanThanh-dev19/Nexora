import { expect, it } from 'vitest'
import { decimalStepMatches } from './decimalStep'

it('accepts browser decimal/exponent inputs and rejects off-step values exactly', () => {
  expect(decimalStepMatches('.3', 0, 0.1)).toBe(true)
  expect(decimalStepMatches('-0.2', 0, 0.1)).toBe(true)
  expect(decimalStepMatches('3e-1', 0, 0.1)).toBe(true)
  expect(decimalStepMatches(0.30000000000000004, 0, 0.1)).toBe(false)
  expect(decimalStepMatches(0.3, 0.1, 0.2)).toBe(true)
  expect(decimalStepMatches(0.3, 0, 0)).toBe(false)
})
