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
import { FORM_STATUS_LABELS } from '../constants/formStatuses'

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
      <Link to="/categories">← Danh mục</Link>
      <div className="page-heading">
        <div>
          <h1>{category?.name ?? 'Danh mục'} — phiên bản biểu mẫu</h1>
          <p className="text-secondary">
            Nội dung biểu mẫu đã phát hành hoặc lưu trữ chỉ được xem.
          </p>
        </div>
        <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
          {mutation.isPending ? 'Đang tạo…' : 'Tạo bản nháp'}
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
            Chưa có phiên bản biểu mẫu. Hãy tạo bản nháp đầu tiên.
          </Alert>
        ) : (
          <Table responsive className="align-middle">
            <thead>
              <tr>
                <th>Phiên bản</th>
                <th>Trạng thái</th>
                <th>Ngày tạo</th>
                <th>Ngày phát hành</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {query.data?.map((form) => (
                <tr key={form.id}>
                  <td>Phiên bản {form.versionNumber}</td>
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
                      {FORM_STATUS_LABELS[form.status] ?? form.status}
                    </Badge>
                  </td>
                  <td>{new Date(form.createdAt).toLocaleString('vi-VN')}</td>
                  <td>
                    {form.publishedAt
                      ? new Date(form.publishedAt).toLocaleString('vi-VN')
                      : '—'}
                  </td>
                  <td>
                    <Button
                      as={Link}
                      to={`/categories/forms/${form.id}`}
                      size="sm"
                      variant="outline-primary"
                    >
                      {form.status === 'DRAFT'
                        ? 'Sửa bản nháp'
                        : 'Xem biểu mẫu'}
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
