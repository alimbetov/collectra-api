import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequirePermission } from './RequirePermission';

const hasPermission = vi.fn<(permission: string) => boolean>();

vi.mock('../model/auth-context', () => ({
  useAuth: () => ({ hasPermission }),
}));

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderGuard(permission = 'CUSTOMER_READ') {
  return render(
    <MemoryRouter initialEntries={['/customers/customer-1']}>
      <Routes>
        <Route
          path="/customers/:customerId"
          element={
            <RequirePermission permission={permission}>
              <div>protected page</div>
            </RequirePermission>
          }
        />
        <Route path="/forbidden" element={<div>forbidden page</div>} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('RequirePermission', () => {
  beforeEach(() => hasPermission.mockReset());

  it('renders a direct deep link when the exact capability is present', () => {
    hasPermission.mockImplementation((permission) => permission === 'CUSTOMER_READ');

    renderGuard();

    expect(screen.getByText('protected page')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/customers/customer-1');
    expect(hasPermission).toHaveBeenCalledWith('CUSTOMER_READ');
  });

  it('fail-closes a direct deep link to /forbidden when capability is absent', async () => {
    hasPermission.mockReturnValue(false);

    renderGuard();

    expect(await screen.findByText('forbidden page')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/forbidden');
    expect(screen.queryByText('protected page')).not.toBeInTheDocument();
  });
});
