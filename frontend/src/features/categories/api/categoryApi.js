import { httpClient } from '../../../shared/api/httpClient'

const data = (request) => request.then((response) => response.data)
export const categoryApi = {
  list: () => data(httpClient.get('/admin/categories')),
  create: (category) => data(httpClient.post('/admin/categories', category)),
  update: (id, category) =>
    data(httpClient.put(`/admin/categories/${id}`, category)),
  versions: (id) =>
    data(httpClient.get(`/admin/categories/${id}/form-versions`)),
  createDraft: (id) =>
    data(httpClient.post(`/admin/categories/${id}/form-versions/drafts`)),
  form: (id) => data(httpClient.get(`/admin/form-versions/${id}`)),
  save: (id, draft) =>
    data(httpClient.put(`/admin/form-versions/${id}`, draft)),
  publish: (id, revision) =>
    data(httpClient.post(`/admin/form-versions/${id}/publish`, { revision })),
  archive: (id, revision) =>
    data(httpClient.post(`/admin/form-versions/${id}/archive`, { revision })),
  skills: () => data(httpClient.get('/admin/skills')),
  slas: () =>
    data(httpClient.get('/sla-policies', { params: { active: true } })),
}
