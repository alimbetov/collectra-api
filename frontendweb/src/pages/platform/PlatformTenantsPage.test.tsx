import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPlatformTenants } from '../../entities/platform/api/platform.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PlatformTenantsPage } from './PlatformTenantsPage';

vi.mock('../../entities/platform/api/platform.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/platform/api/platform.api')>(
    '../../entities/platform/api/platform.api',
  );
  return { ...actual, getPlatformTenants: vi.fn() };
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <MemoryRouter initialEntries={['/platform/tenants?status=ACTIVE']}>
          <PlatformTenantsPage />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getPlatformTenants).mockResolvedValue({
    items: [
      {
        id: '4f80b4f3-5d86-4a74-b105-b0068df1b02f',
        slug: 'acme',
        name: 'ACME',
        status: 'ACTIVE',
        createdAt: '2026-09-20T10:00:00Z',
        updatedAt: '2026-09-22T10:00:00Z',
        revision: 4,
        activeUsers: 12,
        campaignsLast30d: 7,
        messagesLast30d: 240,
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  });
});

describe('PlatformTenantsPage', () => {
  it('renders server tenant registry data and preserves URL-owned filters', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Тенанты' })).toBeInTheDocument();
    expect(await screen.findByText('ACME')).toBeInTheDocument();
    expect(screen.getByText('acme')).toBeInTheDocument();
    expect(screen.getByText('240')).toBeInTheDocument();
    expect(vi.mocked(getPlatformTenants)).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ACTIVE', page: 0, size: 50 }),
    );
  });
});
