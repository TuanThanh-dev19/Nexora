import { readdir, readFile } from 'node:fs/promises'
import { extname, join } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
  ASSET_STATUSES,
  ASSET_STATUS_LABELS,
} from '../features/assets/constants/assetStatuses'
import {
  DYNAMIC_FIELD_TYPES,
  DYNAMIC_FIELD_TYPE_LABELS,
} from '../features/categories/constants/dynamicFieldTypes'
import { FORM_STATUSES } from '../features/categories/constants/formStatuses'
import { LOCATION_TYPES } from '../features/locations/constants/locationTypes'
import {
  TICKET_STATUSES,
  TICKET_STATUS_LABELS,
} from '../features/tickets/constants/ticketStatuses'
import { PRIORITIES } from '../shared/constants/priorities'
import { ROLES, ROLE_CODES, ROLE_LABELS } from '../shared/constants/roles'
import { USER_STATUSES } from '../shared/constants/userStatuses'

const SOURCE_ROOT = join(process.cwd(), 'src')

describe('canonical domain vocabulary', () => {
  it('matches every SQL Server-backed value set exactly', () => {
    expect(ROLES).toEqual(['REQUESTER', 'TECHNICIAN', 'MANAGER', 'ADMIN'])
    expect(USER_STATUSES).toEqual(['ACTIVE', 'LOCKED', 'DISABLED'])
    expect(TICKET_STATUSES).toEqual([
      'SUBMITTED',
      'UNDER_REVIEW',
      'ASSIGNED',
      'IN_PROGRESS',
      'RESOLVED',
      'REOPENED',
      'CLOSED',
      'REJECTED',
      'CANCELLED',
    ])
    expect(DYNAMIC_FIELD_TYPES).toEqual([
      'TEXT',
      'TEXTAREA',
      'NUMBER',
      'SELECT',
      'MULTI_SELECT',
      'DATE',
      'DATETIME',
      'BOOLEAN',
      'IMAGE',
    ])
    expect(ASSET_STATUSES).toEqual([
      'ACTIVE',
      'UNDER_MAINTENANCE',
      'OUT_OF_SERVICE',
      'RETIRED',
    ])
    expect(PRIORITIES).toEqual(['LOW', 'NORMAL', 'HIGH', 'URGENT'])
    expect(FORM_STATUSES).toEqual(['DRAFT', 'PUBLISHED', 'ARCHIVED'])
    expect(LOCATION_TYPES).toEqual([
      'CAMPUS',
      'BUILDING',
      'FLOOR',
      'ROOM',
      'AREA',
    ])
  })

  it('keeps display labels complete and separate from stored codes', () => {
    expect(Object.keys(ROLE_LABELS)).toEqual(ROLES)
    expect(Object.keys(TICKET_STATUS_LABELS)).toEqual(TICKET_STATUSES)
    expect(Object.keys(DYNAMIC_FIELD_TYPE_LABELS)).toEqual(DYNAMIC_FIELD_TYPES)
    expect(Object.keys(ASSET_STATUS_LABELS)).toEqual(ASSET_STATUSES)
  })

  it('accepts the requester role and rejects obsolete role codes', () => {
    const obsoleteRole = ['US', 'ER'].join('')

    expect(ROLES).toContain(ROLE_CODES.REQUESTER)
    expect(ROLES).not.toContain(obsoleteRole)
  })

  it('does not use the obsolete requester role in application source', async () => {
    const obsoleteRole = ['US', 'ER'].join('')
    const obsoleteRoleToken = new RegExp(`\\b${obsoleteRole}\\b`)
    const sourceFiles = await findApplicationSourceFiles(SOURCE_ROOT)
    const offenders = []

    for (const sourceFile of sourceFiles) {
      const source = await readFile(sourceFile, 'utf8')
      if (obsoleteRoleToken.test(source)) {
        offenders.push(sourceFile)
      }
    }

    expect(offenders).toEqual([])
  })
})

async function findApplicationSourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const files = await Promise.all(
    entries.map(async (entry) => {
      const entryPath = join(directory, entry.name)
      if (entry.isDirectory()) {
        return entry.name === 'test'
          ? []
          : findApplicationSourceFiles(entryPath)
      }

      const extension = extname(entry.name)
      const isApplicationModule = extension === '.js' || extension === '.jsx'
      return isApplicationModule && !entry.name.includes('.test.')
        ? [entryPath]
        : []
    }),
  )

  return files.flat()
}
