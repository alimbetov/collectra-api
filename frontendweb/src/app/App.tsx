import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../features/auth/model/auth-context';
import { canAccessNavigationItem, navigation } from './navigation';

export function App() {
  const { user, logout, hasPermission } = useAuth();
  const visibleNavigation = navigation.filter((item) =>
    canAccessNavigationItem(item, hasPermission),
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Collectra</div>
        <nav aria-label="Primary navigation">
          {visibleNavigation.map(({ label, path }) => (
            <NavLink
              key={path}
              to={path}
              end={path === '/'}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="workspace">
        <header className="topbar">
          <div>
            <strong>Operations workspace</strong>
            <span className="environment">Authenticated session</span>
          </div>
          <div className="session-summary" aria-label="Current user">
            <div>
              <strong>{user?.displayName || user?.email}</strong>
              <span>{user?.email}</span>
            </div>
            <button type="button" className="secondary-button" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        </header>
        <section className="page-content">
          <Outlet />
        </section>
      </main>
    </div>
  );
}
