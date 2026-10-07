import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { Alert, Badge, Button, Container, Table } from 'react-bootstrap'
import { categoryApi } from '../api/categoryApi'
import {
  categoryKeys,
  useCategories,
  useVersions,
} from '../hooks/useCategories'
import { QueryState } from '../components/CategoryStates'
import { errorMessage } from '../api/categoryError'

export function CategoryVersionsPage() {
  const { categoryId } = useParams()
  const query = useVersions(categoryId)
  const categories = useCategories()
  const category = categories.data?.find((c) => String(c.id) === categoryId)
  const navigate = useNavigate()
  const client = useQueryClient()
  const mutation = useMutation({
    mutationFn: () => categoryApi.createDraft(categoryId),
    onSuccess: async (form) => {
      await client.invalidateQueries({
        queryKey: categoryKeys.versions(categoryId),
      })
      navigate(`/categories/forms/${form.id}`)
    },
  })
  return (
    <Container className="page-container">
      <Link to="/categories">← Categories</Link>
      <div className="d-flex flex-wrap justify-content-between align-items-start gap-3 my-4">
        <div>
          <h1>{category?.name ?? 'Category'} form versions</h1>
          <p className="text-secondary">
            Published and archived form content is read only.
          </p>
        </div>
        <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
          {mutation.isPending ? 'Creating…' : 'Create draft'}
        </Button>
      </div>
      {mutation.isError && (
        <Alert variant="danger" role="alert">
          {errorMessage(mutation.error)}
        </Alert>
      )}
      <QueryState query={query}>
        {query.data?.length === 0 ? (
          <Alert variant="light">
            No form versions yet. Create the first draft.
          </Alert>
        ) : (
          <Table responsive className="align-middle">
            <thead>
              <tr>
                <th>Version</th>
                <th>Status</th>
                <th>Created</th>
                <th>Published</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {query.data?.map((form) => (
                <tr key={form.id}>
                  <td>Version {form.versionNumber}</td>
                  <td>
                    <Badge
                      bg={
                        {
                          DRAFT: 'warning',
                          PUBLISHED: 'success',
                          ARCHIVED: 'secondary',
                        }[form.status]
                      }
                      text={form.status === 'DRAFT' ? 'dark' : undefined}
                    >
                      {form.status}
                    </Badge>
                  </td>
                  <td>{new Date(form.createdAt).toLocaleString('en-GB')}</td>
                  <td>
                    {form.publishedAt
                      ? new Date(form.publishedAt).toLocaleString('en-GB')
                      : '—'}
                  </td>
                  <td>
                    <Button
                      as={Link}
                      to={`/categories/forms/${form.id}`}
                      size="sm"
                      variant="outline-primary"
                    >
                      {form.status === 'DRAFT' ? 'Edit draft' : 'View form'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </QueryState>
    </Container>
  )
}
