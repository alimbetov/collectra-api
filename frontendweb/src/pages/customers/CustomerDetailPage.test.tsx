import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getCustomer, getCustomerEmails, getCustomerPhones } from '../../entities/customer/api/customer.api';
import { ApiError } from '../../shared/api/http-client';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { CustomerDetailPage } from './CustomerDetailPage';

vi.mock('../../entities/customer/api/customer.api', () => ({
  getCustomer: vi.fn(),
  getCustomerEmails: vi.fn(),
  getCustomerPhones: vi.fn(),
}));

const id = '11111111-1111-4111-8111-111111111111';
const detail = {
  id,
  externalId: 'EXT-1',
  customerType: 'COMPANY' as const,
  displayName: 'Acme Kazakhstan',
  firstName: null,
  lastName: null,
  middleName: null,
  companyName: 'Acme LLP',
  status: 'ACTIVE' as const,
  managerUserId: '22222222-2222-4222-8222-222222222222',
  managerDisplayName: 'Иван Менеджер',
  preferredLocale: 'ru',
  timezone: 'Asia/Almaty',
  customFields: { note: '<script>not html</script>' },
  segmentIds: ['33333333-3333-4333-8333-333333333333'],
  segments: [{ id: '33333333-3333-4333-8333-333333333333', code: 'VIP', name: 'VIP' }],
  createdAt: '2026-09-17T10:00:00Z',
  updatedAt: '2026-09-18T10:00:00Z',
  version: 7,
};

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderPage(entry: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty">
        <MemoryRouter initialEntries={[entry]}>
          <Routes>
            <Route path="/customers/:customerId" element={<CustomerDetailPage tab="overview" />} />
            <Route path="/customers/:customerId/contacts" element={<CustomerDetailPage tab="contacts" />} />
            <Route path="/forbidden" element={<div>Forbidden</div>} />
          </Routes>
          <LocationProbe />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getCustomer).mockResolvedValue(detail);
  vi.mocked(getCustomerEmails).mockResolvedValue([
    { id: 'e1', email: 'billing@acme.test', type: 'WORK', primary: true, verified: false, status: 'ACTIVE', version: 2 },
  ]);
  vi.mocked(getCustomerPhones).mockResolvedValue([
    { id: 'p1', phone: '+7 701 000 00 00', normalizedPhone: '+77010000000', type: 'MOBILE', primary: true, verified: true, status: 'ACTIVE', version: 3 },
  ]);
});

describe('CustomerDetailPage', () => {
  it('renders the overview from one enriched detail request and no contact requests', async () => {
    renderPage(`/customers/${id}`);

    expect(await screen.findByRole('heading', { level: 1, name: 'Acme Kazakhstan' })).toBeInTheDocument();
    expect(screen.getByText('Иван Менеджер')).toBeInTheDocument();
    expect(screen.getByText('VIP')).toBeInTheDocument();
    expect(screen.getByText(/<script>not html<\/script>/)).toBeInTheDocument();
    expect(screen.queryByRole('script')).not.toBeInTheDocument();
    expect(getCustomer).toHaveBeenCalledTimes(1);
    expect(getCustomerEmails).not.toHaveBeenCalled();
    expect(getCustomerPhones).not.toHaveBeenCalled();
  });

  it('loads detail, emails and phones for the direct contacts URL', async () => {
    renderPage(`/customers/${id}/contacts`);

    expect(await screen.findByText('billing@acme.test')).toBeInTheDocument();
    expect(screen.getByText('+7 701 000 00 00')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Разделы карточки клиента' })).toBeInTheDocument();
    expect(getCustomer).toHaveBeenCalledWith(id);
    expect(getCustomerEmails).toHaveBeenCalledWith(id);
    expect(getCustomerPhones).toHaveBeenCalledWith(id);
    expect(getCustomer).toHaveBeenCalledTimes(1);
    expect(getCustomerEmails).toHaveBeenCalledTimes(1);
    expect(getCustomerPhones).toHaveBeenCalledTimes(1);
  });

  it('keeps a successful contact section visible when the other fails', async () => {
    vi.mocked(getCustomerPhones).mockRejectedValue(new ApiError(500, { status: 500, detail: 'secret' }));
    renderPage(`/customers/${id}/contacts`);

    expect(await screen.findByText('billing@acme.test')).toBeInTheDocument();
    expect(screen.getByText('Сервис временно недоступен. Повторите попытку позже.')).toBeInTheDocument();
    expect(screen.queryByText('secret')).not.toBeInTheDocument();
  });

  it('does not request malformed IDs and shows the safe not-found state', () => {
    renderPage('/customers/not-a-uuid');

    expect(screen.getByText('Клиент не найден')).toBeInTheDocument();
    expect(getCustomer).not.toHaveBeenCalled();
    expect(getCustomerEmails).not.toHaveBeenCalled();
    expect(getCustomerPhones).not.toHaveBeenCalled();
  });

  it('maps foreign or missing customers to the same not-found state', async () => {
    vi.mocked(getCustomer).mockRejectedValue(new ApiError(404, { status: 404, code: 'NOT_FOUND' }));
    renderPage(`/customers/${id}`);

    expect(await screen.findByText('Клиент не найден')).toBeInTheDocument();
  });

  it('uses the shared forbidden route for authorization failure', async () => {
    vi.mocked(getCustomer).mockRejectedValue(new ApiError(403, { status: 403 }));
    renderPage(`/customers/${id}`);

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'));
  });
});
