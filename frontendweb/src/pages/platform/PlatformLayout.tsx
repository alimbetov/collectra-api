import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/model/auth-context';
import { useI18n } from '../../shared/i18n/i18n-context';

const navigation = [
  { to: '/platform', label: 'platform.navigation.overview' as const, end: true },
  { to: '/platform/tenants', label: 'platform.navigation.tenants' as const },
  { to: '/platform/users', label: 'platform.navigation.users' as const },
  { to: '/platform/administrators', label: 'platform.navigation.administrators' as const },
  { to: '/platform/analytics', label: 'platform.navigation.analytics' as const },
  { to: '/platform/audit', label: 'platform.navigation.audit' as const },
  { to: '/platform/operations', label: 'platform.navigation.operations' as const },
];

export function PlatformLayout() {
  const { logout, user } = useAuth();
  const { t } = useI18n();

  return (
    <div className="app-shell platform-shell">
      <aside className="sidebar">
        <div className="brand">Collectra</div>
        <div className="platform-shell__scope">{t('platform.scope')}</div>
        <nav aria-label={t('platform.navigation.label')}>
          {navigation.map(({ to, label, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              {t(label)}
            </NavLink>
          ))}
        </nav>
      </aside>

      <main className="workspace">
        <header className="topbar">
          <div>
            <strong>{t('platform.title')}</strong>
            <span className="environment">{t('platform.session')}</span>
          </div>
          <div className="session-summary">
            <div>
              <strong>{user?.email ?? 'super-admin'}</strong>
              <span>PLATFORM_SUPER_ADMIN</span>
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
