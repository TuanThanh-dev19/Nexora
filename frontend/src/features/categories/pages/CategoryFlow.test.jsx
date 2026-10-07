import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'
import {
  render,
  screen,
  waitFor,
  within,
  cleanup,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { CategoryListPage } from './CategoryListPage'
import { CategoryVersionsPage } from './CategoryVersionsPage'
import { FormEditorPage } from './FormEditorPage'
import { RequireRole } from '../../../app/routes/RequireRole'
import { AuthContext } from '../../auth/context/auth-context'

const category = {
  id: 1,
  code: 'IT',
  name: 'IT support',
  active: true,
  assetPolicy: 'OPTIONAL',
  defaultPriority: 'NORMAL',
  locationRequired: true,
}
const skill = { id: 2, code: 'IT', name: 'IT technician', active: true }
const sla = { id: 3, policyName: 'Normal', resolutionMinutes: 60, active: true }
let form, savedPayload, publishCount
const server = setupServer(
  http.get('*/api/v1/admin/categories', () => HttpResponse.json([category])),
  http.get('*/api/v1/admin/categories/1/form-versions', () =>
    HttpResponse.json(form ? [form] : []),
  ),
  http.post('*/api/v1/admin/categories/1/form-versions/drafts', () => {
    form = {
      id: 10,
      category,
      versionNumber: 1,
      revision: 0,
      status: 'DRAFT',
      fields: [],
      requiresEquipment: false,
      minAttachments: 0,
      defaultSkillId: null,
      defaultSlaPolicyId: null,
      createdAt: '2026-10-07T09:00:00Z',
    }
    return HttpResponse.json(form, { status: 201 })
  }),
  http.get('*/api/v1/admin/form-versions/10', () => HttpResponse.json(form)),
  http.get('*/api/v1/admin/skills', () => HttpResponse.json([skill])),
  http.get('*/api/v1/sla-policies', () => HttpResponse.json([sla])),
  http.put('*/api/v1/admin/form-versions/10', async ({ request }) => {
    savedPayload = await request.json()
    form = {
      ...form,
      ...savedPayload,
      revision: form.revision + 1,
      defaultSkill: skill,
      defaultSlaPolicy: sla,
    }
    return HttpResponse.json(form)
  }),
  http.post('*/api/v1/admin/form-versions/10/publish', async ({ request }) => {
    const { revision } = await request.json()
    if (revision !== form.revision)
      return HttpResponse.json({ detail: 'Stale version.' }, { status: 409 })
    publishCount++
    form = {
      ...form,
      revision: revision + 1,
      status: 'PUBLISHED',
      publishedAt: '2026-10-07T10:00:00Z',
    }
    return HttpResponse.json(form)
  }),
)
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterAll(() => server.close())
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
beforeEach(() => {
  form = null
  savedPayload = null
  publishCount = 0
})

function app(path = '/categories', roles = ['ADMIN']) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider value={{ user: { roles }, isChecking: false }}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route element={<RequireRole roles={['ADMIN']} />}>
              <Route path="/categories" element={<CategoryListPage />} />
              <Route
                path="/categories/:categoryId/forms"
                element={<CategoryVersionsPage />}
              />
              <Route
                path="/categories/forms/:formVersionId"
                element={<FormEditorPage />}
              />
            </Route>
            <Route path="/unauthorized" element={<h1>Access denied</h1>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('category administration flow', () => {
  it('creates a draft, edits and saves fields, confirms publication and restores saved data after remount', async () => {
    const user = userEvent.setup()
    const view = app()
    await user.click(
      await screen.findByRole('button', { name: 'Form versions' }),
    )
    expect(
      await screen.findByText('No form versions yet. Create the first draft.'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create draft' }))
    await screen.findByRole('heading', { name: 'IT support · Version 1' })
    await user.selectOptions(await screen.findByLabelText('Default skill'), '2')
    await user.selectOptions(screen.getByLabelText('Default SLA policy'), '3')
    await user.click(screen.getByRole('button', { name: 'Add field' }))
    await user.click(screen.getByLabelText('Field key'))
    await user.paste('description')
    await user.click(screen.getByLabelText('Label', { exact: true }))
    await user.paste('Describe the issue')
    await user.selectOptions(screen.getByLabelText('Field type'), 'TEXTAREA')
    expect(screen.getByRole('button', { name: 'Publish form' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    expect(await screen.findByText('Draft saved.')).toBeInTheDocument()
    expect(savedPayload.fields[0]).toMatchObject({
      fieldKey: 'description',
      fieldType: 'TEXTAREA',
      displayOrder: 0,
      validationRules: {},
    })
    await user.click(screen.getByRole('button', { name: 'Publish form' }))
    const dialog = await screen.findByRole('dialog')
    expect(publishCount).toBe(0)
    await user.click(
      within(dialog).getByRole('button', { name: 'Confirm publish' }),
    )
    expect(
      await screen.findByText(/This form is read only/),
    ).toBeInTheDocument()
    expect(publishCount).toBe(1)
    expect(screen.getByLabelText('Field key')).toBeDisabled()
    view.unmount()
    app('/categories/forms/10')
    expect(await screen.findByDisplayValue('Describe the issue')).toBeDisabled()
    expect(
      screen.queryByRole('button', { name: 'Save draft' }),
    ).not.toBeInTheDocument()
  }, 15000)

  it('shows backend field errors and preserves edits after a failed save', async () => {
    form = {
      id: 10,
      category,
      versionNumber: 1,
      revision: 1,
      status: 'DRAFT',
      requiresEquipment: false,
      minAttachments: 0,
      defaultSkillId: 2,
      defaultSlaPolicyId: 3,
      fields: [
        {
          fieldKey: 'name',
          label: 'Name',
          fieldType: 'TEXT',
          active: true,
          required: false,
          validationRules: {},
          options: [],
        },
      ],
    }
    server.use(
      http.put('*/api/v1/admin/form-versions/10', () =>
        HttpResponse.json(
          {
            detail: 'Invalid form.',
            fieldErrors: { 'fields[0].label': 'Backend rejected this label.' },
          },
          { status: 422 },
        ),
      ),
    )
    const user = userEvent.setup()
    app('/categories/forms/10')
    await user.type(
      await screen.findByLabelText('Label', { exact: true }),
      ' changed',
    )
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    await waitFor(() =>
      expect(
        screen.getAllByText('Backend rejected this label.').length,
      ).toBeGreaterThan(0),
    )
    expect(screen.getByLabelText('Label', { exact: true })).toHaveValue(
      'Name changed',
    )
  })

  it.each(['REQUESTER', 'MANAGER', 'TECHNICIAN'])(
    'guards administration for %s',
    async (role) => {
      app('/categories', [role])
      expect(
        await screen.findByRole('heading', { name: 'Access denied' }),
      ).toBeInTheDocument()
      expect(
        screen.queryByRole('button', { name: 'New category' }),
      ).not.toBeInTheDocument()
    },
  )
})
