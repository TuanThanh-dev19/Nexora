import { describe, expect, it } from 'vitest'
import {
  validateResolution,
  validateWorkImages,
  validateWorkLog,
} from './technicianWorkValidation'
import { canTechnicianWrite, slaState } from '../constants/technicianWork'

const log = {
  version: 0,
  logType: 'PROGRESS',
  content: 'Đang kiểm tra',
  startedAt: '2026-10-10T09:00:00+07:00',
  endedAt: null,
  minutesSpent: null,
}

describe('technician work validation', () => {
  it('requires an active matching assignment, state and ticket version', () => {
    const user = { id: 21, roles: ['TECHNICIAN'], status: 'ACTIVE' }
    const ticket = {
      status: 'ASSIGNED',
      version: 0,
      currentAssignment: { technician: { technicianId: 21 }, endedAt: null },
    }
    expect(canTechnicianWrite(ticket, user, 'ASSIGNED')).toBe(true)
    expect(
      canTechnicianWrite({ ...ticket, version: undefined }, user, 'ASSIGNED'),
    ).toBe(false)
    expect(
      canTechnicianWrite(
        { ...ticket, currentAssignment: null },
        user,
        'ASSIGNED',
      ),
    ).toBe(false)
    expect(
      canTechnicianWrite(ticket, { ...user, status: 'LOCKED' }, 'ASSIGNED'),
    ).toBe(false)
    expect(canTechnicianWrite(ticket, { ...user, id: 24 }, 'ASSIGNED')).toBe(
      false,
    )
    expect(canTechnicianWrite(ticket, user, 'IN_PROGRESS')).toBe(false)
  })
  it('shows SLA risk only for active work', () => {
    const due = new Date('2026-10-10T10:00:00Z').toISOString()
    expect(
      slaState(
        { status: 'ASSIGNED', slaDueAt: due },
        Date.parse('2026-10-10T08:30:00Z'),
      ),
    ).toBe('AT_RISK')
    expect(
      slaState(
        { status: 'IN_PROGRESS', slaDueAt: due },
        Date.parse('2026-10-10T10:01:00Z'),
      ),
    ).toBe('BREACHED')
    expect(
      slaState(
        { status: 'RESOLVED', slaDueAt: due },
        Date.parse('2026-10-10T10:01:00Z'),
      ),
    ).toBeNull()
  })
  it('accepts version zero and optional end/minutes, but rejects invalid time and duration', () => {
    expect(validateWorkLog(log)).toEqual({})
    expect(
      validateWorkLog({
        ...log,
        endedAt: '2026-10-10T08:00:00+07:00',
        minutesSpent: 0,
      }),
    ).toMatchObject({
      endedAt: expect.any(String),
      minutesSpent: expect.any(String),
    })
    expect(
      validateWorkLog({
        ...log,
        content: ' ',
        logType: 'OTHER',
        startedAt: '',
      }),
    ).toMatchObject({
      content: expect.any(String),
      logType: expect.any(String),
      startedAt: expect.any(String),
    })
    expect(
      validateWorkLog({ ...log, startedAt: '2026-10-10T09:00:00' }),
    ).toHaveProperty('startedAt')
    expect(
      validateWorkLog({ ...log, minutesSpent: 2147483648 }),
    ).toHaveProperty('minutesSpent')
  })
  it('requires a saved log, summary and an MVP outcome', () => {
    expect(
      validateResolution(
        { version: 1, resolutionOutcome: 'FIXED', resolutionSummary: 'Xong' },
        1,
      ),
    ).toEqual({})
    expect(
      validateResolution(
        { version: 1, resolutionOutcome: 'DUPLICATE', resolutionSummary: ' ' },
        0,
      ),
    ).toMatchObject({
      resolutionOutcome: expect.any(String),
      resolutionSummary: expect.any(String),
      workLogs: expect.any(String),
    })
  })
  it('checks count, type and size of result images', () => {
    const image = { type: 'image/png', size: 5 * 1024 * 1024 }
    expect(validateWorkImages([image])).toBe('')
    expect(validateWorkImages([])).toMatch(/1 đến 5/)
    expect(validateWorkImages(Array(6).fill(image))).toMatch(/1 đến 5/)
    expect(validateWorkImages([{ ...image, type: 'image/gif' }])).toMatch(
      /JPEG/,
    )
    expect(validateWorkImages([{ ...image, size: image.size + 1 }])).toMatch(
      /5 MiB/,
    )
  })
})
