import { beforeEach, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { Component } from './CreateTicketPreviewPage'
import { createTicketPreviewApi } from '../mocks/createTicketFixture'

beforeEach(() => {
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  URL.createObjectURL = vi.fn(() => 'blob:mock-evidence')
  URL.revokeObjectURL = vi.fn()
})
function open(entry = '/dev/create-ticket/new') {
  const api = createTicketPreviewApi({ delay: 0 })
  const router = createMemoryRouter(
    [
      {
        path: '/dev/create-ticket/:step',
        element: <Component previewApi={api} />,
      },
    ],
    { initialEntries: [entry] },
  )
  render(<RouterProvider router={router} />)
  return { api, router, user: userEvent.setup({ delay: null }) }
}

it('shows the submitted values and linked evidence in the mock detail', async () => {
  const { api, router, user } = open()
  await screen.findByRole('option', { name: 'Thiết bị phòng học' })
  await user.selectOptions(screen.getByLabelText('Danh mục *'), '1')
  await screen.findByText(/Biểu mẫu phiên bản/)
  await user.click(screen.getByLabelText('Tiêu đề *'))
  await user.paste('Preview incident')
  await user.click(screen.getByLabelText('Mô tả *'))
  await user.paste('Details submitted by requester')
  await user.click(screen.getByRole('button', { name: 'Chọn Phòng A101' }))
  await user.type(screen.getByLabelText('Số lượng'), '0')
  await user.selectOptions(screen.getByLabelText('Có thể sử dụng'), 'false')
  await user.selectOptions(screen.getByLabelText('Triệu chứng'), ['a', 'b'])
  await user.upload(
    screen.getByLabelText('Ảnh thiết bị'),
    new File(['image'], 'linked.png', { type: 'image/png' }),
  )
  await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
  await screen.findByRole('heading', {
    name: 'Phiếu mô phỏng MOCK-2026-009001',
  })
  expect(router.state.location.pathname).toBe('/dev/create-ticket/9001')
  expect(screen.getByText('Preview incident')).toBeInTheDocument()
  expect(screen.getByText('Details submitted by requester')).toBeInTheDocument()
  expect(screen.getByText('0')).toBeInTheDocument()
  expect(screen.getByText('Không', { exact: true })).toBeInTheDocument()
  expect(screen.getByText('Lựa chọn A, Lựa chọn B')).toBeInTheDocument()
  expect(screen.getByRole('img', { name: 'linked.png' })).toBeInTheDocument()
  expect(screen.getByText(/KiB · Ảnh thiết bị/)).toBeInTheDocument()
  expect(api.getTicket(9001).attachments).toEqual([
    { fileIndex: 0, fieldKey: 'field8' },
  ])
  await user.click(
    screen.getByRole('link', { name: /Tạo phiếu mô phỏng khác/ }),
  )
  expect(await screen.findByLabelText('Tiêu đề *')).toHaveValue('')
  expect(URL.revokeObjectURL).toHaveBeenCalled()
})
it('does not present an empty creation screen for a missing preview ticket', async () => {
  open('/dev/create-ticket/9999')
  expect(
    screen.getByText(/Phiếu mô phỏng không còn trong bộ nhớ/),
  ).toBeInTheDocument()
  expect(screen.queryByLabelText('Tiêu đề *')).not.toBeInTheDocument()
})
it('shows a failed preview as an error and keeps the entered title', async () => {
  const { user } = open()
  await user.selectOptions(screen.getByLabelText('Kịch bản gửi phiếu'), '403')
  await screen.findByRole('option', { name: 'Thiết bị phòng học' })
  await user.selectOptions(screen.getByLabelText('Danh mục *'), '1')
  await screen.findByText(/Biểu mẫu phiên bản/)
  await user.type(screen.getByLabelText('Tiêu đề *'), 'Keep me')
  await user.type(screen.getByLabelText('Mô tả *'), 'Details')
  await user.click(screen.getByRole('button', { name: 'Chọn Phòng A101' }))
  await user.upload(
    screen.getByLabelText('Ảnh minh chứng chung'),
    new File(['image'], 'general.png', { type: 'image/png' }),
  )
  await user.click(screen.getByRole('button', { name: 'Gửi phiếu' }))
  const error = await screen.findByText('Bạn không có quyền tạo phiếu.')
  expect(error.closest('[role="alert"]')).toHaveClass('alert-danger')
  expect(screen.getByLabelText('Tiêu đề *')).toHaveValue('Keep me')
  expect(
    within(
      screen.getByRole('heading', { name: 'Tạo phiếu yêu cầu' }).parentElement,
    ).queryByText(/Phiếu mô phỏng MOCK/),
  ).not.toBeInTheDocument()
})
