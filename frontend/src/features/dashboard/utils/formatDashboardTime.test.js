import { describe, expect, it, vi } from 'vitest'
import { formatDashboardTime } from './formatDashboardTime'

describe('Bangkok dashboard timestamp', () => {
  it('formats vi-VN in Asia/Bangkok explicitly regardless of device timezone', () => {
    const original = Intl.DateTimeFormat
    const spy = vi.spyOn(Intl, 'DateTimeFormat')
    spy.mockImplementation(function (locale, options) {
      expect(locale).toBe('vi-VN')
      expect(options.timeZone).toBe('Asia/Bangkok')
      return new original(locale, options)
    })
    try {
      expect(formatDashboardTime('2025-12-31T17:30:00Z')).toContain('00:30:00')
      expect(formatDashboardTime('2025-12-31T17:30:00Z')).toContain('1/1/26')
    } finally {
      spy.mockRestore()
    }
  })
})
