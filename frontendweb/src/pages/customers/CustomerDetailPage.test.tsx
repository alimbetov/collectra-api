import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import userEvent from '@testing-library/user-event';
import {
  addCustomerEmail, addCustomerPhone, changeCustomerStatus, getCustomer, getCustomerEmails,
  getCustomerPhones, updateCustomer, updateCustomerEmail, updateCustomerPhone,
} from '../../entities/customer/api/customer.api';
import { ApiError } from '../../shared/api/http-client';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { ToastProvider } from '../../shared/ui';
import { CustomerDetailPage } from './CustomerDetailPage';

vi.mock('../../entities/customer/api/customer.api', () => ({
  getCustomer: vi.fn(),
  getCustomerEmails: vi.fn(),
  getCustomerPhones: vi.fn(),
  updateCustomer: vi.fn(),
  changeCustomerStatus: vi.fn(),
  addCustomerEmail: vi.fn(),
  addCustomerPhone: vi.fn(),
  updateCustomerEmail: vi.fn(),
  updateCustomerPhone: vi.fn(),
}));

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({ hasPermission: () => false }),
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
  const router = createMemoryRouter([
    { path: '/customers/:customerId', element: <><CustomerDetailPage tab="overview" /><LocationProbe /></> },
    { path: '/customers/:customerId/contacts', element: <><CustomerDetailPage tab="contacts" /><LocationProbe /></> },
    { path: '/forbidden', element: <><div>Forbidden</div><LocationProbe /></> },
  ], { initialEntries: [entry] });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty">
        <ToastProvider closeLabel="Закрыть">
          <RouterProvider router={router} />
        </ToastProvider>
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
  vi.mocked(updateCustomer).mockResolvedValue({ ...detail, displayName: 'Acme Updated', version: 8 });
  vi.mocked(changeCustomerStatus).mockResolvedValue({ ...detail, status: 'BLOCKED', version: 8 });
  vi.mocked(addCustomerEmail).mockResolvedValue({ id: 'e2', email: 'new@acme.test', type: 'WORK', primary: false, verified: false, status: 'ACTIVE', version: 0 });
  vi.mocked(addCustomerPhone).mockResolvedValue({ id: 'p2', phone: '+7702', normalizedPhone: '+7702', type: 'MOBILE', primary: false, verified: false, status: 'ACTIVE', version: 0 });
  vi.mocked(updateCustomerEmail).mockResolvedValue({ id: 'e1', email: 'billing@acme.test', type: 'HOME', primary: false, verified: false, status: 'ACTIVE', version: 3 });
  vi.mocked(updateCustomerPhone).mockResolvedValue({ id: 'p1', phone: '+7 701 000 00 00', normalizedPhone: '+77010000000', type: 'MOBILE', primary: false, verified: true, status: 'INACTIVE', version: 4 });
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

  it('edits the full profile with the captured version and preserves manager without USER_READ', async () => {
    const user = userEvent.setup();
    renderPage(`/customers/${id}`);
    await screen.findByRole('heading', { name: 'Acme Kazakhstan' });

    await user.click(screen.getByRole('button', { name: 'Редактировать' }));
    expect(screen.getByText('Изменение менеджера недоступно для вашей роли.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Поиск менеджера')).not.toBeInTheDocument();
    const name = screen.getByLabelText(/Отображаемое имя/);
    await user.clear(name);
    await user.type(name, 'Acme Updated');
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => expect(updateCustomer).toHaveBeenCalledWith(id, expect.objectContaining({
      displayName: 'Acme Updated', managerUserId: detail.managerUserId, version: 7,
    })));
  });

  it('confirms a status change once with the current customer version', async () => {
    const user = userEvent.setup();
    renderPage(`/customers/${id}`);
    await screen.findByRole('heading', { name: 'Acme Kazakhstan' });

    await user.click(screen.getByRole('button', { name: 'Изменить статус' }));
    await user.selectOptions(screen.getByLabelText('Новый статус'), 'BLOCKED');
    await user.click(within(screen.getByRole('dialog', { name: 'Изменение статуса клиента' })).getByRole('button', { name: 'Изменить статус' }));

    await waitFor(() => expect(changeCustomerStatus).toHaveBeenCalledTimes(1));
    expect(changeCustomerStatus).toHaveBeenCalledWith(id, { status: 'BLOCKED', version: 7 });
  });

  it('creates an email without tenant or version fields and refreshes the contacts view', async () => {
    const user = userEvent.setup();
    renderPage(`/customers/${id}/contacts`);
    await screen.findByText('billing@acme.test');

    await user.click(screen.getByRole('button', { name: 'Добавить email' }));
    await user.type(screen.getByLabelText(/Основной email/), 'new@acme.test');
    await user.selectOptions(screen.getByLabelText('Тип'), 'WORK');
    await user.click(screen.getByLabelText('Основной контакт'));
    await user.click(within(screen.getByRole('dialog', { name: 'Добавить email' })).getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => expect(addCustomerEmail).toHaveBeenCalledWith(id, {
      email: 'new@acme.test', type: 'WORK', primary: true,
    }));
    expect(JSON.stringify(vi.mocked(addCustomerEmail).mock.calls)).not.toContain('tenantId');
  });

  it('patches email metadata with the contact version and clears primary when inactive', async () => {
    const user = userEvent.setup();
    renderPage(`/customers/${id}/contacts`);
    const email = await screen.findByText('billing@acme.test');
    const section = email.closest('section');
    expect(section).not.toBeNull();

    await user.click(within(section!).getByRole('button', { name: 'Изменить' }));
    await user.selectOptions(screen.getByLabelText('Статус'), 'INACTIVE');
    expect(screen.getByLabelText('Основной контакт')).not.toBeChecked();
    expect(screen.getByLabelText('Основной контакт')).toBeDisabled();
    await user.click(within(screen.getByRole('dialog', { name: 'Редактирование email' })).getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => expect(updateCustomerEmail).toHaveBeenCalledWith(id, 'e1', {
      type: 'WORK', primary: false, status: 'INACTIVE', version: 2,
    }));
  });
});
