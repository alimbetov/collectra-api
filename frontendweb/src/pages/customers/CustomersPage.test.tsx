import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/http-client';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { CustomersPage } from './CustomersPage';
import {
  getCustomers,
  getManagerOptions,
  getSegmentOptions,
} from '../../entities/customer/api/customer.api';

vi.mock('../../entities/customer/api/customer.api', () => ({
  getCustomers: vi.fn(),
  getManagerOptions: vi.fn(),
  getSegmentOptions: vi.fn(),
}));

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({ hasPermission: (permission: string) => permission === 'USER_READ' }),
}));

const row = {
  id: '11111111-1111-4111-8111-111111111111',
  externalId: 'EXT-1',
  customerType: 'COMPANY' as const,
  displayName: 'Acme Kazakhstan',
  status: 'ACTIVE' as const,
  managerUserId: '22222222-2222-4222-8222-222222222222',
  preferredLocale: 'ru',
  timezone: 'Asia/Almaty',
  segmentIds: ['33333333-3333-4333-8333-333333333333'],
  primaryEmail: 'billing@acme.test',
  primaryPhone: '+77010000000',
  managerDisplayName: 'Иван Менеджер',
  segments: [{ id: '33333333-3333-4333-8333-333333333333', code: 'vip', name: 'VIP' }],
  createdAt: '2026-09-17T10:00:00Z',
  updatedAt: '2026-09-18T10:00:00Z',
};

function page(items = [row], page = 0, totalPages = 2) {
  return { items, page, size: 25, totalElements: items.length ? 51 : 0, totalPages, hasNext: page + 1 < totalPages };
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderPage(entry = '/customers?status=ACTIVE&size=25') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty">
        <MemoryRouter initialEntries={[entry]}>
          <CustomersPage />
          <LocationProbe />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getCustomers).mockResolvedValue(page());
  vi.mocked(getManagerOptions).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
  vi.mocked(getSegmentOptions).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
});

describe('CustomersPage', () => {
  it('restores URL filters and renders the enriched projection without row fan-out', async () => {
    renderPage();

    expect(await screen.findByText('Acme Kazakhstan')).toBeInTheDocument();
    expect(screen.getByText('billing@acme.test')).toBeInTheDocument();
    expect(screen.getByText('Иван Менеджер')).toBeInTheDocument();
    expect(screen.getByText('VIP')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Acme Kazakhstan' })).toHaveAttribute(
      'href',
      `/customers/${row.id}`,
    );
    expect(getCustomers).toHaveBeenCalledWith({ status: 'ACTIVE', size: 25 });
    expect(getCustomers).toHaveBeenCalledTimes(1);
  });

  it('writes pagination to the URL and requests the next page', async () => {
    const user = userEvent.setup();
    vi.mocked(getCustomers).mockImplementation(async (query) => page([row], query.page ?? 0));
    renderPage();
    await screen.findByText('Acme Kazakhstan');

    await user.click(screen.getByRole('button', { name: 'Вперёд' }));
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/customers?status=ACTIVE&page=1&size=25'));
    await waitFor(() => expect(getCustomers).toHaveBeenLastCalledWith({ status: 'ACTIVE', page: 1, size: 25 }));
  });

  it('debounces quick search into one committed URL state', async () => {
    const user = userEvent.setup();
    renderPage('/customers');
    await screen.findByText('Acme Kazakhstan');

    await user.type(screen.getByRole('searchbox', { name: 'Быстрый поиск' }), 'Acme');
    await waitFor(
      () => expect(screen.getByTestId('location')).toHaveTextContent('/customers?search=Acme'),
      { timeout: 1000 },
    );
    await waitFor(() => expect(getCustomers).toHaveBeenCalledTimes(2), { timeout: 1000 });
    expect(getCustomers).toHaveBeenLastCalledWith({ search: 'Acme' });
  });

  it('renders a distinct filtered-empty result', async () => {
    vi.mocked(getCustomers).mockResolvedValue(page([], 0, 0));
    renderPage('/customers?search=missing');

    expect(await screen.findByText('Клиенты не найдены')).toBeInTheDocument();
    expect(screen.getByText('Измените или очистите фильтры.')).toBeInTheDocument();
  });

  it('uses the shared forbidden route for authorization failure', async () => {
    vi.mocked(getCustomers).mockRejectedValue(new ApiError(403, { status: 403 }));
    renderPage();

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'));
  });

  it('returns an out-of-range empty page to the canonical first page', async () => {
    vi.mocked(getCustomers).mockResolvedValue(page([], 5, 0));
    renderPage('/customers?page=5');

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/customers'));
  });
});
