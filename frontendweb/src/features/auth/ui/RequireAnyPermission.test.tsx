import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequireAnyPermission } from './RequireAnyPermission';

const hasPermission = vi.fn<(permission: string) => boolean>();

vi.mock('../model/auth-context', () => ({
  useAuth: () => ({ hasPermission }),
}));

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderGuard(permissions = ['INTEGRATION_SOURCE_READ', 'SERVICE_CLIENT_READ']) {
  return render(
    <MemoryRouter initialEntries={['/integrations']}>
      <Routes>
        <Route
          path="/integrations"
          element={
            <RequireAnyPermission permissions={permissions}>
              <div>protected page</div>
            </RequireAnyPermission>
          }
        />
        <Route path="/forbidden" element={<div>forbidden page</div>} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('RequireAnyPermission', () => {
  beforeEach(() => hasPermission.mockReset());

  it('allows a direct route when any declared capability is present', () => {
    hasPermission.mockImplementation((permission) => permission === 'SERVICE_CLIENT_READ');
    renderGuard();

    expect(screen.getByText('protected page')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/integrations');
  });

  it('fail-closes a direct route when none of the declared capabilities are present', async () => {
    hasPermission.mockReturnValue(false);
    renderGuard();

    expect(await screen.findByText('forbidden page')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/forbidden');
    expect(screen.queryByText('protected page')).not.toBeInTheDocument();
  });
});
