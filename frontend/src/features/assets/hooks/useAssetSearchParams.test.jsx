import { renderHook, act } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import {
  isPositiveId,
  readAssetSearchParams,
  useAssetSearchParams,
} from './useAssetSearchParams'

function createWrapper(initialUrl = '/assets') {
  return function Wrapper({ children }) {
    return <MemoryRouter initialEntries={[initialUrl]}>{children}</MemoryRouter>
  }
}

describe('useAssetSearchParams', () => {
  it('isPositiveId validates string IDs', () => {
    expect(isPositiveId('1')).toBe(true)
    expect(isPositiveId('123')).toBe(true)
    expect(isPositiveId('0')).toBe(false)
    expect(isPositiveId('-1')).toBe(false)
    expect(isPositiveId('abc')).toBe(false)
    expect(isPositiveId(null)).toBe(false)
  })

  it('readAssetSearchParams returns defaults when empty', () => {
    const search = new URLSearchParams()
    const result = readAssetSearchParams(search)

    expect(result).toEqual({
      page: '0',
      size: '20',
      sort: 'createdAt,desc',
      locationId: '',
      status: '',
    })
  })

  it('sanitizes invalid sort and status', () => {
    const search = new URLSearchParams('sort=malicious&status=INVALID_STATUS')
    const result = readAssetSearchParams(search)

    expect(result.sort).toBe('createdAt,desc')
    expect(result.status).toBe('')
  })

  it('reads valid query params properly', () => {
    const search = new URLSearchParams(
      'page=2&size=50&sort=assetCode,asc&locationId=10&status=ACTIVE',
    )
    const result = readAssetSearchParams(search)

    expect(result).toEqual({
      page: '2',
      size: '50',
      sort: 'assetCode,asc',
      locationId: '10',
      status: 'ACTIVE',
    })
  })

  it('updates parameters and resets page to 0 when filter changes', () => {
    const { result } = renderHook(() => useAssetSearchParams(), {
      wrapper: createWrapper('/assets?page=3&status=ACTIVE'),
    })

    expect(result.current.params.page).toBe('3')

    act(() => {
      result.current.update({ status: 'UNDER_MAINTENANCE' })
    })

    expect(result.current.params.status).toBe('UNDER_MAINTENANCE')
    expect(result.current.params.page).toBe('0')
  })

  it('keeps page when only page is updated', () => {
    const { result } = renderHook(() => useAssetSearchParams(), {
      wrapper: createWrapper('/assets?page=1'),
    })

    act(() => {
      result.current.update({ page: 2 })
    })

    expect(result.current.params.page).toBe('2')
  })

  it('resets all filters on reset()', () => {
    const { result } = renderHook(() => useAssetSearchParams(), {
      wrapper: createWrapper('/assets?page=2&status=ACTIVE&locationId=5'),
    })

    act(() => {
      result.current.reset()
    })

    expect(result.current.params).toEqual({
      page: '0',
      size: '20',
      sort: 'createdAt,desc',
      locationId: '',
      status: '',
    })
  })
})
