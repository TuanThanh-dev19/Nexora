import { Button, Container, Nav, Navbar } from 'react-bootstrap'
import { NavLink, Outlet } from 'react-router'
import {
  FiGrid,
  FiLogIn,
  FiLogOut,
  FiPackage,
  FiTag,
  FiSliders,
} from 'react-icons/fi'
import { useAuth } from '../../features/auth/context/useAuth'
import { ROLE_CODES } from '../constants/roles'

const ASSET_MANAGEMENT_ROLES = [ROLE_CODES.MANAGER, ROLE_CODES.ADMIN]

export function AppLayout() {
  const { user, logout } = useAuth()
  const name = user?.fullName ?? user?.displayName ?? 'Tài khoản'

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>
      <Navbar expand="lg" collapseOnSelect className="app-navbar">
        <Container>
          <Navbar.Brand as={NavLink} to="/" className="brand">
            <span className="brand-mark">
              <img src="/logo.jpg" alt="Nexora Logo" />
            </span>
            <span className="brand-name">
              Nexora<small>CAMPUS CARE</small>
            </span>
          </Navbar.Brand>
          <Navbar.Toggle aria-controls="main-navigation" />
          <Navbar.Collapse id="main-navigation">
            <Nav className="me-auto app-navigation" activeKey={null}>
              <Nav.Link as={NavLink} to="/dashboard" eventKey="dashboard">
                <FiGrid aria-hidden="true" /> Tổng quan
              </Nav.Link>
              <Nav.Link as={NavLink} to="/tickets" eventKey="tickets">
                <FiTag aria-hidden="true" /> Phiếu yêu cầu
              </Nav.Link>
              {user?.roles.includes(ROLE_CODES.ADMIN) && (
                <Nav.Link as={NavLink} to="/categories" eventKey="categories">
                  <FiSliders aria-hidden="true" /> Categories
                </Nav.Link>
              )}
              {user?.roles.some((role) =>
                ASSET_MANAGEMENT_ROLES.includes(role),
              ) && (
                <Nav.Link as={NavLink} to="/assets" eventKey="assets">
                  <FiPackage aria-hidden="true" /> Tài sản
                </Nav.Link>
              )}
            </Nav>
            {user ? (
              <div className="account-menu">
                <span className="account-avatar" aria-hidden="true">
                  {name.trim().charAt(0).toUpperCase()}
                </span>
                <span className="account-details">
                  <strong>{name}</strong>
                  <small>{user.roles.join(' · ')}</small>
                </span>
                <Button variant="outline-secondary" size="sm" onClick={logout}>
                  <FiLogOut aria-hidden="true" /> Đăng xuất
                </Button>
              </div>
            ) : (
              <NavLink to="/login" className="btn btn-primary btn-sm">
                <FiLogIn aria-hidden="true" /> Đăng nhập
              </NavLink>
            )}
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <main id="main-content" className="app-main" tabIndex={-1}>
        <Outlet />
      </main>
      <footer className="app-footer">
        <Container>
          <span>
            <strong>Nexora</strong> · Campus care
          </span>
          <span>Kết nối để chăm sóc không gian chung.</span>
        </Container>
      </footer>
    </div>
  )
}
