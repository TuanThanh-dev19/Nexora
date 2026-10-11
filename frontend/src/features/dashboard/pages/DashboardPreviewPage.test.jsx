import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll, expect, it } from 'vitest'
import { createDashboardMock } from '../mocks/dashboardHandlers'
import { Component } from './DashboardPreviewPage'

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  delete window.dashboardMock
})
afterAll(() => server.close())

it('switches actual MSW preview scenarios and role copy without logging into the app', async () => {
  const mock = createDashboardMock('*/api/v1')
  server.use(...mock.handlers)
  window.dashboardMock = { setScenario: mock.setScenario }
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  render(
    <QueryClientProvider client={client}>
      <Component />
    </QueryClientProvider>,
  )
  const user = userEvent.setup()
  expect(screen.getByText(/PREVIEW \/ MOCK/)).toBeInTheDocument()
  expect(await screen.findByText('12')).toBeInTheDocument()
  await user.selectOptions(
    screen.getByLabelText('Vai trò minh họa'),
    'TECHNICIAN,ADMIN',
  )
  expect(
    screen.getByText('Tổng quan toàn bộ phiếu yêu cầu.'),
  ).toBeInTheDocument()
  await user.selectOptions(screen.getByLabelText('Trạng thái minh họa'), 'zero')
  expect(
    await screen.findByText(
      'Chưa có hoạt động phiếu yêu cầu trong phạm vi của bạn.',
    ),
  ).toBeInTheDocument()
  await user.selectOptions(
    screen.getByLabelText('Trạng thái minh họa'),
    'loading',
  )
  expect(
    await screen.findByLabelText('Đang tải Phiếu đang mở'),
  ).toBeInTheDocument()
  await user.selectOptions(
    screen.getByLabelText('Trạng thái minh họa'),
    'error',
  )
  expect(
    await screen.findByRole('button', { name: 'Thử lại' }),
  ).toBeInTheDocument()
  await user.selectOptions(
    screen.getByLabelText('Trạng thái minh họa'),
    'retry',
  )
  await user.click(await screen.findByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('12')).toBeInTheDocument()
}, 15000)
