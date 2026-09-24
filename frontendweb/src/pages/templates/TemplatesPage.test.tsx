import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getTemplates } from '../../entities/template/api/template.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { TemplatesPage } from './TemplatesPage';

vi.mock('../../entities/template/api/template.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/template/api/template.api')>(
    '../../entities/template/api/template.api',
  );
  return { ...actual, getTemplates: vi.fn() };
});

beforeEach(() => {
  vi.mocked(getTemplates).mockResolvedValue({
    items: [
      {
        id: '11111111-1111-1111-1111-111111111111',
        code: 'PAYMENT_REMINDER',
        name: 'Payment reminder',
        documentType: 'NOTIFICATION',
        status: 'ACTIVE',
        createdAt: '2026-09-20T10:00:00Z',
        updatedAt: '2026-09-22T10:00:00Z',
        version: 2,
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
  });
});

describe('TemplatesPage', () => {
  it('renders registry data and forwards URL-owned filters', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    render(
      <QueryClientProvider client={client}>
        <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
          <MemoryRouter initialEntries={['/templates?channel=EMAIL&locale=ru&status=ACTIVE']}>
            <TemplatesPage />
          </MemoryRouter>
        </I18nProvider>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { name: 'Шаблоны' })).toBeInTheDocument();
    expect(await screen.findByText('Payment reminder')).toBeInTheDocument();
    expect(screen.getByText('PAYMENT_REMINDER')).toBeInTheDocument();

    expect(vi.mocked(getTemplates)).toHaveBeenCalledWith(
      expect.objectContaining({
        channel: 'EMAIL',
        locale: 'ru',
        status: 'ACTIVE',
        page: 0,
        size: 50,
      }),
    );
  });
});
