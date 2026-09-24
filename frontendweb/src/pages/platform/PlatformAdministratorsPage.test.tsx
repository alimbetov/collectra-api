import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPlatformAdministrators } from '../../entities/platform/api/platform.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PlatformAdministratorsPage } from './PlatformAdministratorsPage';

vi.mock('../../entities/platform/api/platform.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/platform/api/platform.api')>(
    '../../entities/platform/api/platform.api',
  );
  return { ...actual, getPlatformAdministrators: vi.fn() };
});

beforeEach(() => {
  vi.mocked(getPlatformAdministrators).mockResolvedValue({
    items: [
      {
        id: '11111111-1111-1111-1111-111111111111',
        email: 'super-admin@example.test',
        status: 'ACTIVE',
        authorizationVersion: 2,
        createdAt: '2026-09-20T10:00:00Z',
        updatedAt: '2026-09-22T10:00:00Z',
        revision: 4,
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  });
});

describe('PlatformAdministratorsPage', () => {
  it('renders server-paged administrator data', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
          <MemoryRouter initialEntries={['/platform/administrators?status=ACTIVE']}>
            <PlatformAdministratorsPage />
          </MemoryRouter>
        </I18nProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { name: 'Администраторы платформы' }))
      .toBeInTheDocument();
    expect(await screen.findByText('super-admin@example.test')).toBeInTheDocument();
    expect(vi.mocked(getPlatformAdministrators)).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ACTIVE', page: 0, size: 50 }),
    );
  });
});
