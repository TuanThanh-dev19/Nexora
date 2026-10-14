import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import {
  TicketAttachments,
  TicketDynamicValues,
  TicketStatusHistory,
} from './TicketDetailSections'
import { detail } from '../testsupport/ticketFixtures'
import { formatTicketDate } from '../utils/ticketFormatting'

afterEach(cleanup)
function renderValue(type, raw) {
  return render(
    <TicketDynamicValues
      formVersion={{
        fields: [
          {
            id: 1,
            label: 'Câu trả lời',
            fieldType: type,
            options: [{ value: 'old', label: 'Lựa chọn cũ' }],
          },
        ],
      }}
      dynamicValues={
        raw === undefined ? [] : [{ fieldDefinitionId: 1, valueJson: raw }]
      }
      attachments={[]}
    />,
  )
}
describe('ticket detail sections', () => {
  it('renders all canonical types, historical option labels and image metadata without scalar IMAGE values', () => {
    const { container } = render(<TicketDynamicValues {...detail} />)
    expect(screen.getByText('Nội dung <script>')).toBeInTheDocument()
    expect(screen.getByText(/Dòng một/)).toHaveTextContent('Dòng một Dòng hai')
    expect(screen.getByText('9007199254740993')).toBeInTheDocument()
    expect(screen.getAllByText('Lựa chọn cũ')).toHaveLength(2)
    expect(screen.getByText('Lựa chọn mới')).toBeInTheDocument()
    expect(screen.getByText('Không')).toBeInTheDocument()
    expect(screen.getByText('bằng chứng.png')).toBeInTheDocument()
    expect(screen.getByText(/120 byte/)).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('script')).toBeNull()
    expect(screen.getByText('8 thg 10, 2026')).toBeInTheDocument()
  })
  it.each([
    ['NUMBER', '0', '0'],
    ['BOOLEAN', 'false', 'Không'],
    ['BOOLEAN', 'true', 'Có'],
    ['TEXT', '""', 'Chuỗi rỗng'],
    ['TEXT', 'null', 'Giá trị trống'],
    ['TEXT', undefined, 'Chưa có dữ liệu'],
    ['SELECT', '"missing"', 'missing'],
    ['MULTI_SELECT', '[]', 'Không có lựa chọn'],
  ])('keeps %s value %s distinct', (type, raw, expected) => {
    renderValue(type, raw)
    expect(screen.getByText(expected)).toBeInTheDocument()
  })
  it.each([
    ['BOOLEAN', '"false"'],
    ['NUMBER', '"12"'],
    ['TEXT', '{bad'],
    ['MULTI_SELECT', '[1]'],
    ['DATE', '"2026-02-30"'],
    ['DATETIME', '"invalid"'],
  ])('safely renders invalid %s values', (type, raw) => {
    renderValue(type, raw)
    expect(screen.getByText('Không đọc được giá trị')).toBeInTheDocument()
  })
  it('preserves calendar dates independently of browser timezone', () => {
    expect(formatTicketDate('2026-10-08')).toBe('8 thg 10, 2026')
    expect(formatTicketDate('2026-02-30')).toBeNull()
  })
  it('renders initial and same-status history in API order with nullable reason', () => {
    render(<TicketStatusHistory history={detail.statusHistory} />)
    const entries = screen.getAllByRole('listitem')
    expect(entries).toHaveLength(2)
    expect(within(entries[0]).getByText('Gửi báo cáo')).toBeInTheDocument()
    expect(entries[0]).toHaveTextContent('Khởi tạo → Đã gửi')
    expect(entries[1]).toHaveTextContent('Đã phân công → Đã phân công')
    expect(entries[1]).toHaveTextContent('Đổi kỹ thuật viên')
  })
  it('renders metadata only with safe original filenames and no download link', () => {
    const files = [
      { ...detail.attachments[0], originalName: '<script>name</script>.png' },
      detail.attachments[1],
    ]
    const { container } = render(<TicketAttachments attachments={files} />)
    expect(screen.getByText('<script>name</script>.png')).toBeInTheDocument()
    expect(screen.getByText(/image\/png/)).toBeInTheDocument()
    expect(screen.getByText(/512 byte/)).toBeInTheDocument()
    expect(screen.getByText(/Kỹ thuật viên/)).toBeInTheDocument()
    expect(
      screen
        .getAllByRole('button', { name: /Xem ảnh/ })
        .every((button) => button.disabled),
    ).toBe(true)
    expect(screen.queryByRole('link')).toBeNull()
    expect(container.querySelector('script')).toBeNull()
  })
  it('provides separate empty states for form, history and attachments', () => {
    render(
      <>
        <TicketDynamicValues
          formVersion={{ fields: [] }}
          dynamicValues={[]}
          attachments={[]}
        />
        <TicketStatusHistory history={[]} />
        <TicketAttachments attachments={[]} />
      </>,
    )
    expect(
      screen.getByText('Biểu mẫu không có trường dữ liệu.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Chưa có lịch sử trạng thái.')).toBeInTheDocument()
    expect(screen.getByText('Chưa có tệp đính kèm.')).toBeInTheDocument()
  })
})
