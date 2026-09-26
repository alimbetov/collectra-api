import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequireAuth } from './RequireAuth';

const auth = vi.hoisted(() => ({
  value: { sessionKind: 'tenant', status: 'authenticated' },
}));

vi.mock('../model/auth-context', () => ({
  useAuth: () => auth.value,
}));

vi.mock('../../../shared/i18n/i18n-context', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

function LoginProbe() {
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  return <output data-testid="login-from">{from ?? ''}</output>;
}

function renderGuard(entry = '/customers/customer-1?tab=contacts#primary') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/customers/:customerId"
          element={
            <RequireAuth>
              <div>tenant page</div>
            </RequireAuth>
          }
        />
        <Route path="/login" element={<LoginProbe />} />
        <Route path="/platform" element={<div>platform page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAuth direct-link semantics', () => {
  beforeEach(() => {
    auth.value = { sessionKind: 'tenant', status: 'authenticated' };
  });

  it('preserves pathname, query and hash when redirecting an unauthenticated deep link', async () => {
    auth.value = { sessionKind: 'none', status: 'unauthenticated' };

    renderGuard();

    expect(await screen.findByTestId('login-from')).toHaveTextContent(
      '/customers/customer-1?tab=contacts#primary',
    );
  });

  it('keeps tenant-authenticated users on the requested deep link', () => {
    renderGuard();

    expect(screen.getByText('tenant page')).toBeInTheDocument();
  });

  it('keeps platform sessions out of tenant business routes', async () => {
    auth.value = { sessionKind: 'platform', status: 'authenticated' };

    renderGuard();

    expect(await screen.findByText('platform page')).toBeInTheDocument();
    expect(screen.queryByText('tenant page')).not.toBeInTheDocument();
  });
});
