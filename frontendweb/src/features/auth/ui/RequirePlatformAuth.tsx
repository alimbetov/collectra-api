import type { PropsWithChildren } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../model/auth-context';
import { useI18n } from '../../../shared/i18n/i18n-context';

export function RequirePlatformAuth({ children }: PropsWithChildren) {
  const { hasRole, status } = useAuth();
  const location = useLocation();
  const { t } = useI18n();

  if (status === 'loading') {
    return (
      <div className="auth-loading" role="status" aria-live="polite">
        {t('auth.restoring')}
      </div>
    );
  }

  if (status === 'unauthenticated') {
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from }} />;
  }

  return hasRole('PLATFORM_SUPER_ADMIN') ? children : <Navigate to="/forbidden" replace />;
}
