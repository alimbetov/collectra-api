import type { PropsWithChildren } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../model/auth-context';

interface RequirePermissionProps extends PropsWithChildren {
  permission: string;
}

export function RequirePermission({ permission, children }: RequirePermissionProps) {
  const { hasPermission } = useAuth();
  return hasPermission(permission) ? children : <Navigate to="/forbidden" replace />;
}
