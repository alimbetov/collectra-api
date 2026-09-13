import { NavLink, Outlet } from 'react-router-dom';

const navigation = [
  ['Dashboard', '/'],
  ['Customers', '/customers'],
  ['Receivables', '/receivables'],
  ['Collections', '/collections'],
  ['Campaigns', '/campaigns'],
  ['Templates', '/templates'],
  ['Imports', '/imports'],
  ['Files', '/files'],
] as const;

export function App() {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Collectra</div>
        <nav aria-label="Primary navigation">
          {navigation.map(([label, path]) => (
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
            <span className="environment">Frontend shell</span>
          </div>
          <div className="user-placeholder" aria-label="Current user">
            Session pending FW1
          </div>
        </header>
        <section className="page-content">
          <Outlet />
        </section>
      </main>
    </div>
  );
}
