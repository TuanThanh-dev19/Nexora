export const INITIAL_MOCK_SKILLS = [
  { id: 1, code: 'ELEC', name: 'Điện dân dụng & công nghiệp', active: true },
  { id: 2, code: 'HVAC', name: 'Hệ thống điều hòa không khí', active: true },
  { id: 3, code: 'PLUMB', name: 'Hệ thống cấp thoát nước', active: true },
  { id: 4, code: 'NETWORK', name: 'Mạng viễn thông & CNTT', active: true },
  { id: 5, code: 'FIRE', name: 'Hệ thống PCCC cơ sở', active: true },
  { id: 6, code: 'CARP', name: 'Mộc & Xây dựng cơ bản', active: true },
]

export const INITIAL_MOCK_LOCATIONS = [
  { id: 1, code: 'CS1', name: 'Cơ sở 1 (Khu A)', type: 'CAMPUS', parentId: null, active: true },
  { id: 2, code: 'TOA-A', name: 'Tòa nhà A', type: 'BUILDING', parentId: 1, active: true },
  { id: 3, code: 'TANG-1', name: 'Tầng 1 (Tòa A)', type: 'FLOOR', parentId: 2, active: true },
  { id: 4, code: 'P101', name: 'Phòng 101', type: 'ROOM', parentId: 3, active: true },
  { id: 5, code: 'CS2', name: 'Cơ sở 2 (Khu B)', type: 'CAMPUS', parentId: null, active: true },
  { id: 6, code: 'TOA-B', name: 'Tòa nhà B', type: 'BUILDING', parentId: 5, active: true },
]

export const INITIAL_MOCK_TECHNICIANS = [
  {
    technicianId: 3,
    email: 'tech1@nexora.campus',
    fullName: 'Nguyễn Kỹ Thuật',
    status: 'ACTIVE',
    roles: ['TECHNICIAN'],
    available: true,
    maxActiveTickets: 5,
    version: 0,
    skills: [
      {
        skillId: 1,
        skillCode: 'ELEC',
        skillName: 'Điện dân dụng & công nghiệp',
        proficiencyLevel: 4,
      },
      {
        skillId: 2,
        skillCode: 'HVAC',
        skillName: 'Hệ thống điều hòa không khí',
        proficiencyLevel: 3,
      },
    ],
    serviceAreas: [
      {
        locationId: 1,
        locationCode: 'CS1',
        locationName: 'Cơ sở 1 (Khu A)',
      },
      {
        locationId: 2,
        locationCode: 'TOA-A',
        locationName: 'Tòa nhà A',
      },
    ],
  },
  {
    technicianId: 7,
    email: 'tech.manager@nexora.campus',
    fullName: 'Hoàng Đa Năng',
    status: 'ACTIVE',
    roles: ['TECHNICIAN', 'MANAGER'],
    available: true,
    maxActiveTickets: 3,
    version: 0,
    skills: [
      {
        skillId: 4,
        skillCode: 'NETWORK',
        skillName: 'Mạng viễn thông & CNTT',
        proficiencyLevel: 5,
      },
    ],
    serviceAreas: [
      {
        locationId: 1,
        locationCode: 'CS1',
        locationName: 'Cơ sở 1 (Khu A)',
      },
    ],
  },
  {
    technicianId: 8,
    email: 'tech2@nexora.campus',
    fullName: 'Võ Minh Trí',
    status: 'ACTIVE',
    roles: ['TECHNICIAN'],
    available: false,
    maxActiveTickets: 4,
    version: 0,
    skills: [
      {
        skillId: 3,
        skillCode: 'PLUMB',
        skillName: 'Hệ thống cấp thoát nước',
        proficiencyLevel: 2,
      },
    ],
    serviceAreas: [],
  },
]

let mockTechnicians = JSON.parse(JSON.stringify(INITIAL_MOCK_TECHNICIANS))
let mockSkills = JSON.parse(JSON.stringify(INITIAL_MOCK_SKILLS))
let mockLocations = JSON.parse(JSON.stringify(INITIAL_MOCK_LOCATIONS))
let nextFailure = null

export function resetMockTechnicians() {
  mockTechnicians = JSON.parse(JSON.stringify(INITIAL_MOCK_TECHNICIANS))
  mockSkills = JSON.parse(JSON.stringify(INITIAL_MOCK_SKILLS))
  mockLocations = JSON.parse(JSON.stringify(INITIAL_MOCK_LOCATIONS))
  nextFailure = null
}

export function failNextTechnicianAction(status, detail = 'Simulated error') {
  nextFailure = { status, detail }
}

function checkNextFailure() {
  if (nextFailure) {
    const failure = nextFailure
    nextFailure = null
    const error = new Error(failure.detail)
    error.response = {
      status: failure.status,
      data: {
        status: failure.status,
        detail: failure.detail,
      },
    }
    throw error
  }
}

export const technicianMockAdapter = {
  async listTechnicians(params = {}) {
    checkNextFailure()
    const page = Number(params.page ?? 0)
    const size = Number(params.size ?? 20)
    let filtered = [...mockTechnicians]

    if (params.query) {
      const q = String(params.query).trim().toLowerCase()
      filtered = filtered.filter(
        (t) =>
          t.fullName.toLowerCase().includes(q) ||
          t.email.toLowerCase().includes(q),
      )
    }

    if (params.available !== undefined && params.available !== '') {
      const isAvail =
        params.available === true ||
        params.available === 'true' ||
        params.available === 1 ||
        params.available === '1'
      filtered = filtered.filter((t) => t.available === isAvail)
    }

    const totalElements = filtered.length
    const totalPages = Math.ceil(totalElements / size) || 1
    const start = page * size
    const content = filtered.slice(start, start + size).map((t) => ({
      technicianId: t.technicianId,
      fullName: t.fullName,
      email: t.email,
      status: t.status,
      available: t.available,
      maxActiveTickets: t.maxActiveTickets,
      version: t.version,
    }))

    return {
      content,
      items: content,
      totalElements,
      totalPages,
      page,
      size,
    }
  },

  async getTechnicianById(technicianId) {
    checkNextFailure()
    let id = technicianId
    if (id === 'me') {
      id = 3
    } else {
      id = Number(id)
    }

    const tech = mockTechnicians.find((t) => t.technicianId === id)
    if (!tech) {
      const error = new Error(`Technician profile not found for id: ${id}`)
      error.response = {
        status: 404,
        data: { status: 404, detail: `Technician profile not found for id: ${id}` },
      }
      throw error
    }

    return JSON.parse(JSON.stringify(tech))
  },

  async updateProfile(technicianId, payload) {
    checkNextFailure()
    let id = technicianId === 'me' ? 3 : Number(technicianId)
    const tech = mockTechnicians.find((t) => t.technicianId === id)
    if (!tech) {
      const error = new Error('Technician not found')
      error.response = { status: 404, data: { detail: 'Technician not found' } }
      throw error
    }

    if (payload.version != null && payload.version !== tech.version) {
      const error = new Error('Stale version.')
      error.response = {
        status: 409,
        data: { status: 409, detail: 'Stale version.' },
      }
      throw error
    }

    if (payload.maxActiveTickets != null && payload.maxActiveTickets <= 0) {
      const error = new Error('maxActiveTickets must be greater than zero')
      error.response = {
        status: 400,
        data: {
          status: 400,
          fieldErrors: { maxActiveTickets: 'Sức chứa tối đa phải là số nguyên lớn hơn 0' },
        },
      }
      throw error
    }

    tech.available = Boolean(payload.available)
    tech.maxActiveTickets = Number(payload.maxActiveTickets)
    tech.version += 1

    return JSON.parse(JSON.stringify(tech))
  },

  async updateSkills(technicianId, payload) {
    checkNextFailure()
    let id = technicianId === 'me' ? 3 : Number(technicianId)
    const tech = mockTechnicians.find((t) => t.technicianId === id)
    if (!tech) {
      const error = new Error('Technician not found')
      error.response = { status: 404, data: { detail: 'Technician not found' } }
      throw error
    }

    if (payload.version != null && payload.version !== tech.version) {
      const error = new Error('Stale version.')
      error.response = {
        status: 409,
        data: { status: 409, detail: 'Stale version.' },
      }
      throw error
    }

    const assignedSkills = (payload.skills ?? []).map((item) => {
      const catalog = mockSkills.find((s) => s.id === Number(item.skillId))
      if (item.proficiencyLevel < 1 || item.proficiencyLevel > 5) {
        const error = new Error('proficiencyLevel must be between 1 and 5')
        error.response = {
          status: 400,
          data: { status: 400, detail: 'proficiencyLevel must be between 1 and 5' },
        }
        throw error
      }
      return {
        skillId: Number(item.skillId),
        skillCode: catalog?.code ?? `SKILL-${item.skillId}`,
        skillName: catalog?.name ?? `Kỹ năng ${item.skillId}`,
        proficiencyLevel: Number(item.proficiencyLevel),
      }
    })

    tech.skills = assignedSkills
    tech.version += 1

    return JSON.parse(JSON.stringify(tech))
  },

  async updateServiceAreas(technicianId, payload) {
    checkNextFailure()
    let id = technicianId === 'me' ? 3 : Number(technicianId)
    const tech = mockTechnicians.find((t) => t.technicianId === id)
    if (!tech) {
      const error = new Error('Technician not found')
      error.response = { status: 404, data: { detail: 'Technician not found' } }
      throw error
    }

    if (payload.version != null && payload.version !== tech.version) {
      const error = new Error('Stale version.')
      error.response = {
        status: 409,
        data: { status: 409, detail: 'Stale version.' },
      }
      throw error
    }

    const assignedAreas = (payload.locationIds ?? []).map((locId) => {
      const loc = mockLocations.find((l) => l.id === Number(locId))
      return {
        locationId: Number(locId),
        locationCode: loc?.code ?? `LOC-${locId}`,
        locationName: loc?.name ?? `Địa điểm ${locId}`,
      }
    })

    tech.serviceAreas = assignedAreas
    tech.version += 1

    return JSON.parse(JSON.stringify(tech))
  },

  async getSkills() {
    checkNextFailure()
    return JSON.parse(JSON.stringify(mockSkills.filter((s) => s.active)))
  },

  async getLocations(parentId) {
    checkNextFailure()
    const pId = parentId != null ? Number(parentId) : null
    return JSON.parse(
      JSON.stringify(
        mockLocations.filter((l) => l.parentId === pId && l.active),
      ),
    )
  },
}
