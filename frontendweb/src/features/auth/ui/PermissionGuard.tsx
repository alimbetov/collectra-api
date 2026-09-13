import type { ReactNode } from 'react';
import { useAuth } from '../model/auth-context';

interface PermissionGuardProps {
  permission: string;
  children: ReactNode;
  fallback?: ReactNode;
}

export function PermissionGuard({ permission, children, fallback = null }: PermissionGuardProps) {
  const { hasPermission } = useAuth();
  return hasPermission(permission) ? children : fallback;
}
