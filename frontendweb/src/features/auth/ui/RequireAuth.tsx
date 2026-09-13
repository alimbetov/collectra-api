import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../model/auth-context';
import type { PropsWithChildren } from 'react';

export function RequireAuth({ children }: PropsWithChildren) {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <div className="auth-loading">Restoring session…</div>;
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}
