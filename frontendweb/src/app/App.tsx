import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../features/auth/model/auth-context';
import { canAccessNavigationItem, navigation } from './navigation';
import { useI18n } from '../shared/i18n/i18n-context';

export function App() {
  const { user, logout, hasPermission } = useAuth();
  const { t } = useI18n();
  const visibleNavigation = navigation.filter((item) =>
    canAccessNavigationItem(item, hasPermission),
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Collectra</div>
        <nav aria-label={t('shell.navigation')}>
          {visibleNavigation.map(({ labelKey, path }) => (
            <NavLink
              key={path}
              to={path}
              end={path === '/'}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {t(labelKey)}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="workspace">
        <header className="topbar">
          <div>
            <strong>{t('shell.workspace')}</strong>
            <span className="environment">{t('shell.session')}</span>
          </div>
          <div className="session-summary" aria-label={t('shell.currentUser')}>
            <div>
              <strong>{user?.displayName || user?.email}</strong>
              <span>{user?.email}</span>
            </div>
            <button type="button" className="secondary-button" onClick={() => void logout()}>
              {t('shell.signOut')}
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
