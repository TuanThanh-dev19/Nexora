// @vitest-environment node
import { afterAll, afterEach, beforeAll, expect, it, vi } from 'vitest'
import { httpClient } from '../../../shared/api/httpClient'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { createTicketApi } from './createTicketApi'
import { buildTicketMultipart } from '../utils/createTicketContract'
import { createFormFixture } from '../mocks/createTicketFixture'

const server = setupServer()
const storage = new Map()
beforeAll(() => {
  vi.stubGlobal('sessionStorage', {
    getItem: (k) => storage.get(k),
    setItem: (k, v) => storage.set(k, v),
    clear: () => storage.clear(),
  })
  vi.stubGlobal('window', { dispatchEvent: vi.fn() })
  httpClient.defaults.baseURL = 'http://localhost/api/v1'
  server.listen({ onUnhandledRequest: 'error' })
})
afterEach(() => {
  server.resetHandlers()
  sessionStorage.clear()
})
afterAll(() => {
  server.close()
  vi.unstubAllGlobals()
})

it('sends multipart with JSON ticket part, files and session authentication', async () => {
  let captured
  server.use(
    http.post('*/api/v1/tickets', async ({ request }) => {
      expect(request.headers.get('content-type')).toMatch(
        /^multipart\/form-data; boundary=/,
      )
      expect(request.headers.get('authorization')).toBe('Bearer test-token')
      const form = await request.formData()
      expect(form.get('ticket').type).toBe('application/json')
      captured = JSON.parse(await form.get('ticket').text())
      expect(form.get('files').name).toBe('photo.png')
      return HttpResponse.json(
        { id: 9, ticketNumber: 'NX-9', status: 'SUBMITTED', version: 0 },
        { status: 201, headers: { Location: '/api/v1/tickets/9' } },
      )
    }),
  )
  sessionStorage.setItem('nexora.session.token', 'test-token')
  const payload = buildTicketMultipart(
    createFormFixture,
    {
      categoryId: 1,
      locationId: 2,
      title: 'Broken',
      description: 'Details',
      values: { field7: false },
    },
    [
      {
        file: new File(['x'], 'photo.png', { type: 'image/png' }),
        fieldKey: 'field8',
      },
    ],
  )
  expect(await createTicketApi.submit(payload)).toMatchObject({
    id: 9,
    ticketNumber: 'NX-9',
  })
  expect(captured.attachments).toEqual([{ fileIndex: 0, fieldKey: 'field8' }])
  expect(captured.dynamicValues).toEqual([{ fieldKey: 'field7', value: false }])
})
it.each([400, 401, 403, 404, 409, 413, 415, 422])(
  'preserves Problem Details for HTTP %s',
  async (status) => {
    server.use(
      http.post('*/api/v1/tickets', () =>
        HttpResponse.json(
          { detail: 'Rejected', fieldErrors: { title: 'Invalid' } },
          { status },
        ),
      ),
    )
    await expect(createTicketApi.submit(new FormData())).rejects.toMatchObject({
      response: { status, data: { fieldErrors: { title: 'Invalid' } } },
    })
  },
)
it('requests active categories, published form, active children and paged eligible assets', async () => {
  server.use(
    http.get('*/api/v1/categories', ({ request }) => {
      expect(new URL(request.url).searchParams.get('active')).toBe('true')
      return HttpResponse.json([])
    }),
    http.get('*/api/v1/categories/1/published-form', () =>
      HttpResponse.json(createFormFixture),
    ),
    http.get('*/api/v1/locations', ({ request }) => {
      const params = new URL(request.url).searchParams
      expect(params.get('active')).toBe('true')
      expect(params.get('parentId')).toBe('2')
      return HttpResponse.json([])
    }),
    http.get('*/api/v1/locations/2/eligible-assets', ({ request }) => {
      const params = new URL(request.url).searchParams
      expect(params.get('query')).toBe('AC')
      expect(params.get('page')).toBe('1')
      return HttpResponse.json({ content: [] })
    }),
  )
  await createTicketApi.categories()
  expect((await createTicketApi.form(1)).status).toBe('PUBLISHED')
  await createTicketApi.locations(2)
  await createTicketApi.assets(2, 'AC', 1)
})
