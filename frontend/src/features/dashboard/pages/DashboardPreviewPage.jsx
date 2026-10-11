import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Alert, Container, Form } from 'react-bootstrap'
import { AuthContext } from '../../auth/context/auth-context'
import { DashboardPage } from './DashboardPage'
import { DASHBOARD_SCENARIOS } from '../mocks/dashboardHandlers'

export function Component() {
  const [role, setRole] = useState('REQUESTER')
  const [scenario, setScenario] = useState('success')
  const queryClient = useQueryClient()
  async function changeScenario(value) {
    await queryClient.cancelQueries({ queryKey: ['dashboard'] })
    window.dashboardMock.setScenario(value)
    setScenario(value)
    queryClient.removeQueries({ queryKey: ['dashboard'] })
  }
  return (
    <>
      <Container className="pt-3">
        <Alert variant="warning">
          PREVIEW / MOCK — dữ liệu minh họa, không lưu database và không kiểm
          chứng phân quyền backend.
        </Alert>
        <div className="d-flex flex-wrap gap-3">
          <Form.Group>
            <Form.Label htmlFor="dashboard-preview-role">
              Vai trò minh họa
            </Form.Label>
            <Form.Select
              id="dashboard-preview-role"
              value={role}
              onChange={(event) => setRole(event.target.value)}
            >
              {[
                'REQUESTER',
                'TECHNICIAN',
                'MANAGER',
                'ADMIN',
                'REQUESTER,TECHNICIAN',
                'TECHNICIAN,ADMIN',
              ].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </Form.Select>
          </Form.Group>
          <Form.Group>
            <Form.Label htmlFor="dashboard-preview-state">
              Trạng thái minh họa
            </Form.Label>
            <Form.Select
              id="dashboard-preview-state"
              value={scenario}
              onChange={(event) => void changeScenario(event.target.value)}
            >
              {DASHBOARD_SCENARIOS.map((value) => (
                <option key={value}>{value}</option>
              ))}
            </Form.Select>
          </Form.Group>
        </div>
      </Container>
      <AuthContext.Provider
        value={{ user: { id: 'dashboard-preview', roles: role.split(',') } }}
      >
        <DashboardPage key={`${role}-${scenario}`} />
      </AuthContext.Provider>
    </>
  )
}
