import type { PropsWithChildren } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../model/auth-context';

interface RequireAnyPermissionProps extends PropsWithChildren {
  permissions: readonly string[];
}

export function RequireAnyPermission({ permissions, children }: RequireAnyPermissionProps) {
  const { hasPermission } = useAuth();
  return permissions.some(hasPermission) ? children : <Navigate to="/forbidden" replace />;
}
