import { useState } from 'react'
import { Alert, Container, Form } from 'react-bootstrap'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { CreateTicketPage } from './CreateTicketPage'
import { createTicketPreviewApi } from '../mocks/createTicketFixture'

export function Component() {
  const [client] = useState(
    () => new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  )
  const [scenario, setScenario] = useState('success')
  const [api] = useState(() => createTicketPreviewApi())
  const location = useLocation()
  const created = location.state?.createdTicketNumber
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
        {created ? (
          <Alert variant="success">
            <h1>Phiếu mô phỏng {created}</h1>
            <p>Trạng thái: SUBMITTED. Dữ liệu chưa được lưu vào máy chủ.</p>
            <Link to="/dev/create-ticket/new">Tạo phiếu mô phỏng khác</Link>
          </Alert>
        ) : null}
      </Container>
      {!created && (
        <CreateTicketPage
          api={api}
          detailBase="/dev/create-ticket"
          backPath="/"
        />
      )}
    </QueryClientProvider>
  )
}
