import type { PropsWithChildren } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../model/auth-context';

export function RequireAuth({ children }: PropsWithChildren) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return (
      <div className="auth-loading" role="status" aria-live="polite">
        Restoring session…
      </div>
    );
  }

  if (status === 'unauthenticated') {
    const from = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from }} />;
  }

  return children;
}
