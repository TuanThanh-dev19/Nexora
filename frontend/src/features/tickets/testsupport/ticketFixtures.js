export const summary = {
  id: 10,
  ticketNumber: 'NX-2026-000010',
  title: 'Máy chiếu không khởi động',
  status: 'SUBMITTED',
  priority: 'HIGH',
  category: { id: 1, code: 'AV', name: 'Thiết bị trình chiếu' },
  location: { id: 2, code: 'R201', name: 'Phòng 201' },
  asset: { id: 3, assetCode: 'PROJ-03' },
  reporter: { id: 4, fullName: 'Nguyễn A' },
  slaDueAt: '2026-10-09T10:00:00+07:00',
  createdAt: '2026-10-08T08:30:00+07:00',
  updatedAt: '2026-10-08T08:30:00+07:00',
}
const types = [
  'TEXT',
  'TEXTAREA',
  'NUMBER',
  'SELECT',
  'MULTI_SELECT',
  'DATE',
  'DATETIME',
  'BOOLEAN',
  'IMAGE',
]
const raw = [
  '"Nội dung <script>"',
  '"Dòng một\\nDòng hai"',
  '9007199254740993',
  '"old"',
  '["old","new"]',
  '"2026-10-08"',
  '"2026-10-08T08:30:00+07:00"',
  'false',
]
export const detail = {
  ...summary,
  description: 'Mô tả lỗi\nchi tiết',
  version: 3,
  formVersion: {
    id: 20,
    versionNumber: 1,
    formStatus: 'ARCHIVED',
    fields: types.map((type, index) => ({
      id: index + 1,
      fieldKey: `field_${index}`,
      label: `Trường ${type}`,
      fieldType: type,
      displayOrder: index,
      required: false,
      options: ['SELECT', 'MULTI_SELECT'].includes(type)
        ? [
            { value: 'old', label: 'Lựa chọn cũ' },
            { value: 'new', label: 'Lựa chọn mới' },
          ]
        : [],
    })),
  },
  dynamicValues: raw.map((valueJson, index) => ({
    fieldDefinitionId: index + 1,
    valueJson,
  })),
  statusHistory: [
    {
      id: 1,
      fromStatus: null,
      toStatus: 'SUBMITTED',
      transitionAction: 'SUBMIT',
      reason: null,
      changedBy: summary.reporter,
      changedAt: summary.createdAt,
    },
    {
      id: 2,
      fromStatus: 'ASSIGNED',
      toStatus: 'ASSIGNED',
      transitionAction: 'REASSIGN',
      reason: 'Đổi kỹ thuật viên',
      changedBy: { id: 5, fullName: 'Quản lý' },
      changedAt: summary.createdAt,
    },
  ],
  attachments: [
    {
      id: 1,
      fieldDefinitionId: 9,
      attachmentType: 'REPORT_EVIDENCE',
      originalName: 'bằng chứng.png',
      contentType: 'image/png',
      sizeBytes: 120,
      uploadedBy: summary.reporter,
      createdAt: summary.createdAt,
    },
    {
      id: 2,
      fieldDefinitionId: null,
      attachmentType: 'WORK_RESULT',
      originalName: 'kết quả.pdf',
      contentType: 'application/pdf',
      sizeBytes: 512,
      uploadedBy: { id: 6, fullName: 'Kỹ thuật viên' },
      createdAt: summary.createdAt,
    },
  ],
}
export function page(content = [summary], overrides = {}) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    ...overrides,
  }
}
