import { Button, Container, Nav, Navbar } from 'react-bootstrap'
import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../../features/auth/context/useAuth'
import { ROLE_CODES } from '../constants/roles'

const ASSET_MANAGEMENT_ROLES = [ROLE_CODES.MANAGER, ROLE_CODES.ADMIN]

export function AppLayout() {
  const { user, logout } = useAuth()

  return (
    <>
      <Navbar bg="dark" data-bs-theme="dark" expand="lg">
        <Container>
          <Navbar.Brand as={NavLink} to="/">
            Nexora
          </Navbar.Brand>
          <Navbar.Toggle aria-controls="main-navigation" />
          <Navbar.Collapse id="main-navigation">
            <Nav className="me-auto">
              <Nav.Link as={NavLink} to="/dashboard">
                Dashboard
              </Nav.Link>
              <Nav.Link as={NavLink} to="/tickets">
                Tickets
              </Nav.Link>
              {user?.roles.includes(ROLE_CODES.ADMIN) && (
                <Nav.Link as={NavLink} to="/categories">
                  Categories
                </Nav.Link>
              )}
              {user?.roles.some((role) =>
                ASSET_MANAGEMENT_ROLES.includes(role),
              ) && (
                <Nav.Link as={NavLink} to="/assets">
                  Assets
                </Nav.Link>
              )}
            </Nav>
            {user ? (
              <div className="d-flex align-items-center gap-3 text-light">
                <small>
                  {user.displayName} · {user.roles.join(', ')}
                </small>
                <Button variant="outline-light" size="sm" onClick={logout}>
                  Log out
                </Button>
              </div>
            ) : (
              <NavLink to="/login" className="btn btn-outline-light btn-sm">
                Log in
              </NavLink>
            )}
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <main>
        <Outlet />
      </main>
    </>
  )
}
