import { useState } from 'react'
import { Button, Container, Nav, Navbar, NavDropdown } from 'react-bootstrap'
import { NavLink, Outlet, useLocation } from 'react-router'
import {
  FiGrid,
  FiLogIn,
  FiLogOut,
  FiPlus,
  FiTag,
  FiTool,
  FiUsers,
} from 'react-icons/fi'
import { useAuth } from '../../features/auth/context/useAuth'
import { navigationForRoles } from '../constants/navigation'
import { SessionCheckingSpinner } from '../components/SessionCheckingSpinner'

const NAV_ICONS = { grid: FiGrid, tag: FiTag, plus: FiPlus, tool: FiTool }

export function AppLayout() {
  const [expanded, setExpanded] = useState(false)
  const location = useLocation()
  const { user, logout, isChecking } = useAuth()
  const name = user?.fullName ?? user?.displayName ?? 'Tài khoản'
  const links = user && !isChecking ? navigationForRoles(user.roles) : []
  const management = links.filter((item) => item.group === 'management')

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>
      <Navbar
        expand="xl"
        collapseOnSelect
        className="app-navbar"
        expanded={expanded}
        onToggle={setExpanded}
      >
        <Container>
          <Navbar.Brand
            as={NavLink}
            to="/"
            className="brand"
            onClick={() => setExpanded(false)}
          >
            <span className="brand-mark">
              <img src="/logo.jpg" alt="Nexora Logo" />
            </span>
            <span className="brand-name">
              Nexora<small>CAMPUS CARE</small>
            </span>
          </Navbar.Brand>
          <Navbar.Toggle
            aria-controls="main-navigation"
            label="Mở hoặc đóng điều hướng"
            aria-expanded={expanded}
          />
          <Navbar.Collapse id="main-navigation">
            <Nav className="me-auto app-navigation" activeKey={null}>
              {links
                .filter((item) => !item.group)
                .map((item) => {
                  const Icon = NAV_ICONS[item.icon]
                  return (
                    <Nav.Link
                      as={NavLink}
                      to={item.path}
                      end={item.end && location.pathname === '/tickets/new'}
                      eventKey={item.path}
                      key={item.path}
                    >
                      <Icon aria-hidden="true" /> {item.label}
                    </Nav.Link>
                  )
                })}
              {management.length > 0 && (
                <NavDropdown
                  title={
                    <>
                      <FiUsers aria-hidden="true" /> Quản lý
                    </>
                  }
                  id="management-navigation"
                >
                  {management.map((item) => (
                    <NavDropdown.Item
                      as={NavLink}
                      to={item.path}
                      end={item.end && location.pathname === '/technicians/me'}
                      eventKey={item.path}
                      key={item.path}
                    >
                      {item.label}
                    </NavDropdown.Item>
                  ))}
                </NavDropdown>
              )}
            </Nav>

            {isChecking ? (
              <SessionCheckingSpinner />
            ) : user ? (
              <div className="account-menu">
                <span className="account-avatar" aria-hidden="true">
                  {name.trim().charAt(0).toUpperCase()}
                </span>
                <span className="account-details">
                  <strong title={name}>{name}</strong>
                  <small>{user.roles.join(' · ')}</small>
                </span>
                <Button variant="outline-secondary" size="sm" onClick={logout}>
                  <FiLogOut aria-hidden="true" /> Đăng xuất
                </Button>
              </div>
            ) : (
              <NavLink
                to="/login"
                className="btn btn-primary btn-sm"
                onClick={() => setExpanded(false)}
              >
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
