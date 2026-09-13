import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { MeDto } from '../../../entities/user/model/user.types';
import { subscribeSessionLost } from '../../../shared/auth/auth-events';
import {
  clearTokens,
  getRefreshToken,
  hasRefreshToken,
  setTokens,
} from '../../../shared/auth/token-storage';
import {
  getCurrentUser,
  loginBySlug,
  logout as logoutRequest,
  type LoginBySlugRequest,
} from '../api/auth.api';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  status: AuthStatus;
  user: MeDto | null;
  login: (request: LoginBySlugRequest) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<MeDto | null>(null);

  const clearSession = useCallback(() => {
    clearTokens();
    setUser(null);
    setStatus('unauthenticated');
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => subscribeSessionLost(clearSession), [clearSession]);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      if (!hasRefreshToken()) {
        if (active) {
          setStatus('unauthenticated');
        }
        return;
      }

      try {
        const currentUser = await getCurrentUser();
        if (active) {
          setUser(currentUser);
          setStatus('authenticated');
        }
      } catch {
        if (active) {
          clearSession();
        }
      }
    }

    void bootstrap();
    return () => {
      active = false;
    };
  }, [clearSession]);

  const login = useCallback(
    async (request: LoginBySlugRequest) => {
      setStatus('loading');
      try {
        const tokens = await loginBySlug(request);
        setTokens(tokens);
        const currentUser = await getCurrentUser();
        setUser(currentUser);
        setStatus('authenticated');
      } catch (error) {
        clearSession();
        throw error;
      }
    },
    [clearSession],
  );

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        await logoutRequest(refreshToken);
      }
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      login,
      logout,
      hasPermission: (permission) => user?.permissions.includes(permission) ?? false,
    }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
