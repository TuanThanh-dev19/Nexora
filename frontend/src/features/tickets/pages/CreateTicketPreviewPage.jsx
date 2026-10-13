import { useState } from 'react'
import { Alert, Container, Form } from 'react-bootstrap'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { CreateTicketPage } from './CreateTicketPage'
import { createTicketPreviewApi } from '../mocks/createTicketFixture'
import { CreateTicketPreviewDetail } from '../components/CreateTicketPreviewDetail'

export function Component({ previewApi }) {
  const [client] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  )
  const [scenario, setScenario] = useState('success')
  const [api] = useState(() => previewApi ?? createTicketPreviewApi())
  const { step } = useParams()
  const isNew = step === 'new'
  return (
    <QueryClientProvider client={client}>
      <Container className="pt-3">
        <Alert variant="warning">
          PREVIEW / MOCK — không lưu database. Dữ liệu và thao tác ở trang này
          được mô phỏng riêng.
        </Alert>
        <Form.Label htmlFor="create-preview-scenario">
          Kịch bản gửi phiếu
        </Form.Label>
        <Form.Select
          id="create-preview-scenario"
          value={scenario}
          onChange={(e) => {
            setScenario(e.target.value)
            api.setScenario(e.target.value)
          }}
        >
          {[
            'success',
            '400',
            '401',
            '403',
            '404',
            '409',
            '413',
            '415',
            'network',
          ].map((x) => (
            <option key={x}>{x}</option>
          ))}
        </Form.Select>
      </Container>
      {isNew ? (
        <CreateTicketPage
          api={api}
          detailBase="/dev/create-ticket"
          backPath="/"
        />
      ) : (
        <CreateTicketPreviewDetail ticket={api.getTicket(step)} />
      )}
    </QueryClientProvider>
  )
}
