// oxlint-disable react/only-export-components -- Test-only render harness, not an application refresh boundary.
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router'
import { AuthContext } from '../../auth/context/auth-context'
import { TicketListPage } from '../pages/TicketListPage'
import { TicketDetailPage } from '../pages/TicketDetailPage'
import { MyWorkPage } from '../pages/MyWorkPage'

export function NavigationProbe() {
  const location = useLocation()
  const navigate = useNavigate()
  return (
    <>
      <output aria-label="URL hiện tại">
        {location.pathname + location.search}
      </output>
      <button onClick={() => navigate(-1)}>Browser back</button>
      <button onClick={() => navigate(1)}>Browser forward</button>
    </>
  )
}
export function renderTicketApp(
  entry = '/tickets',
  user = { id: 4, roles: ['REQUESTER'] },
  client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } }),
) {
  const result = render(
    <QueryClientProvider client={client}>
      <AuthContext.Provider
        value={{ user, isAuthenticated: true, isChecking: false }}
      >
        <MemoryRouter initialEntries={[entry]}>
          <NavigationProbe />
          <Routes>
            <Route path="/tickets" element={<TicketListPage />} />
            <Route path="/tickets/:ticketId" element={<TicketDetailPage />} />
            <Route path="/my-work" element={<MyWorkPage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
  return { ...result, client }
}
