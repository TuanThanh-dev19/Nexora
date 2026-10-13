export const createFormFixture = {
  id: 501,
  versionNumber: 1,
  status: 'PUBLISHED',
  minAttachments: 1,
  requiresEquipment: false,
  category: {
    id: 1,
    name: 'Thiết bị phòng học',
    active: true,
    assetPolicy: 'OPTIONAL',
    locationRequired: true,
  },
  fields: [
    'TEXT',
    'TEXTAREA',
    'NUMBER',
    'SELECT',
    'MULTI_SELECT',
    'DATE',
    'DATETIME',
    'BOOLEAN',
    'IMAGE',
  ].map((fieldType, i) => ({
    id: i + 1,
    fieldKey: `field${i}`,
    fieldType,
    label: {
      TEXT: 'Tên thiết bị',
      TEXTAREA: 'Thông tin thêm',
      NUMBER: 'Số lượng',
      SELECT: 'Mức ảnh hưởng',
      MULTI_SELECT: 'Triệu chứng',
      DATE: 'Ngày phát hiện',
      DATETIME: 'Thời điểm phát hiện',
      BOOLEAN: 'Có thể sử dụng',
      IMAGE: 'Ảnh thiết bị',
    }[fieldType],
    displayOrder: i,
    active: true,
    required: false,
    validationRules: {},
    helpText: `Trường ${fieldType}`,
    options: ['SELECT', 'MULTI_SELECT'].includes(fieldType)
      ? [
          {
            id: 1,
            optionValue: 'a',
            optionLabel: 'Lựa chọn A',
            active: true,
            displayOrder: 0,
          },
          {
            id: 2,
            optionValue: 'b',
            optionLabel: 'Lựa chọn B',
            active: true,
            displayOrder: 1,
          },
        ]
      : [],
  })),
}

// Used only by the development preview route; never intercepts runtime requests.
export function createTicketPreviewApi() {
  let scenario = 'success'
  let revision = 1
  return {
    setScenario(value) {
      scenario = value
    },
    async categories() {
      return [createFormFixture.category]
    },
    async form() {
      return {
        ...createFormFixture,
        id: 500 + revision,
        versionNumber: revision,
      }
    },
    async locations(parentId) {
      return parentId
        ? []
        : [
            {
              id: 1,
              code: 'A101',
              name: 'Phòng A101',
              type: 'ROOM',
              active: true,
            },
          ]
    },
    async assets() {
      return {
        content: [
          { id: 1, assetCode: 'AC-001', equipmentType: { name: 'Máy lạnh' } },
        ],
        totalPages: 1,
      }
    },
    async submit(payload) {
      await new Promise((resolve) => setTimeout(resolve, 600))
      if (!payload.get('ticket')) throw new Error('Missing ticket part')
      if (scenario !== 'success') {
        if (scenario === '409') revision++
        const error = new Error('Preview failure')
        if (scenario !== 'network')
          error.response = {
            status: Number(scenario),
            data: {
              fieldErrors:
                scenario === '400'
                  ? { title: 'Tiêu đề không hợp lệ (mock).' }
                  : {},
            },
          }
        throw error
      }
      return {
        id: 9001,
        ticketNumber: 'MOCK-2026-009001',
        status: 'SUBMITTED',
        version: 0,
      }
    },
  }
}
