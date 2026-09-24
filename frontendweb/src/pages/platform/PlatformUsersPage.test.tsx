import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPlatformUsers } from '../../entities/platform/api/platform.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PlatformUsersPage } from './PlatformUsersPage';

vi.mock('../../entities/platform/api/platform.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/platform/api/platform.api')>(
    '../../entities/platform/api/platform.api',
  );
  return { ...actual, getPlatformUsers: vi.fn() };
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <MemoryRouter initialEntries={['/platform/users?membershipStatus=ACTIVE&tenantSlug=acme']}>
          <PlatformUsersPage />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getPlatformUsers).mockResolvedValue({
    items: [
      {
        userId: '11111111-1111-1111-1111-111111111111',
        membershipId: '22222222-2222-2222-2222-222222222222',
        tenantId: '33333333-3333-3333-3333-333333333333',
        tenantSlug: 'acme',
        tenantName: 'ACME',
        email: 'operator@acme.test',
        displayName: 'Operator',
        accountStatus: 'ACTIVE',
        membershipStatus: 'ACTIVE',
        effectiveAccessStatus: 'ACTIVE',
        roleCodes: ['TENANT_USER'],
        createdAt: '2026-09-20T10:00:00Z',
        updatedAt: '2026-09-22T10:00:00Z',
        revision: 3,
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  });
});

describe('PlatformUsersPage', () => {
  it('renders paged users and forwards URL-owned filters', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Пользователи платформы' }))
      .toBeInTheDocument();
    expect(await screen.findByText('Operator')).toBeInTheDocument();
    expect(screen.getByText('operator@acme.test')).toBeInTheDocument();
    expect(screen.getByText('TENANT_USER')).toBeInTheDocument();

    expect(vi.mocked(getPlatformUsers)).toHaveBeenCalledWith(
      expect.objectContaining({
        membershipStatus: 'ACTIVE',
        tenantSlug: 'acme',
        page: 0,
        size: 50,
      }),
    );
  });
});
