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
  getSessionKind,
  getRefreshToken,
  hasRefreshToken,
  setTokens,
  type AuthSessionKind,
} from '../../../shared/auth/token-storage';
import {
  getCurrentPlatformUser,
  getCurrentUser,
  loginBySlug,
  loginPlatform,
  logout as logoutRequest,
  logoutPlatform,
  type LoginBySlugRequest,
  type PlatformLoginRequest,
  type PlatformMeDto,
} from '../api/auth.api';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';
export type AuthUser =
  | ({ kind: 'tenant' } & MeDto)
  | ({ kind: 'platform'; id: string; email: string; roles: string[]; permissions: string[] });

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  sessionKind: AuthSessionKind | null;
  login: (request: LoginBySlugRequest) => Promise<void>;
  loginPlatform: (request: PlatformLoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (permission: string) => boolean;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);
  const [sessionKind, setSessionKind] = useState<AuthSessionKind | null>(null);

  const clearSession = useCallback(() => {
    clearTokens();
    setUser(null);
    setSessionKind(null);
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
        const kind = getSessionKind();
        const currentUser =
          kind === 'platform'
            ? platformUser(await getCurrentPlatformUser())
            : tenantUser(await getCurrentUser());
        if (active) {
          setUser(currentUser);
          setSessionKind(kind);
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
        setTokens(tokens, 'tenant');
        const currentUser = await getCurrentUser();
        setUser(tenantUser(currentUser));
        setSessionKind('tenant');
        setStatus('authenticated');
      } catch (error) {
        clearSession();
        throw error;
      }
    },
    [clearSession],
  );

  const platformLogin = useCallback(
    async (request: PlatformLoginRequest) => {
      setStatus('loading');
      try {
        const tokens = await loginPlatform(request);
        setTokens(tokens, 'platform');
        const currentUser = await getCurrentPlatformUser();
        setUser(platformUser(currentUser));
        setSessionKind('platform');
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
    const kind = getSessionKind();
    try {
      if (refreshToken) {
        if (kind === 'platform') {
          await logoutPlatform(refreshToken);
        } else {
          await logoutRequest(refreshToken);
        }
      }
    } catch {
      // Browser session cleanup must succeed even if server-side revocation is temporarily unavailable.
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      sessionKind,
      login,
      loginPlatform: platformLogin,
      logout,
      hasPermission: (permission) => user?.permissions.includes(permission) ?? false,
      hasRole: (role) => user?.roles.includes(role) ?? false,
    }),
    [status, user, sessionKind, login, platformLogin, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function tenantUser(user: MeDto): AuthUser {
  return { ...user, kind: 'tenant' };
}

function platformUser(user: PlatformMeDto): AuthUser {
  return {
    kind: 'platform',
    id: user.userId,
    email: 'super-admin',
    roles: ['PLATFORM_SUPER_ADMIN'],
    permissions: [],
  };
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
