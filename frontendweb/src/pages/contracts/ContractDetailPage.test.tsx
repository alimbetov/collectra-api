import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getContract, transitionContract, updateContract } from '../../entities/contract/api/contract.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { ToastProvider } from '../../shared/ui';
import { ContractDetailPage } from './ContractDetailPage';

vi.mock('../../entities/contract/api/contract.api', () => ({ getContract: vi.fn(), transitionContract: vi.fn(), updateContract: vi.fn() }));

const id = '11111111-1111-4111-8111-111111111111';
const contract = {
  id, customerId: '22222222-2222-4222-8222-222222222222', customerExternalId: 'C-1', customerDisplayName: 'Acme',
  externalId: 'EXT-1', contractNumber: 'CN-001', status: 'ACTIVE' as const, validFrom: '2026-01-01', validTo: null,
  renewalDate: null, customFields: { note: '<script>safe text</script>' }, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z', version: 4,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([
    { path: '/contracts/:contractId', element: <ContractDetailPage /> },
    { path: '/contracts', element: <div>Contracts</div> },
    { path: '/customers/:customerId', element: <div>Customer</div> },
    { path: '/forbidden', element: <div>Forbidden</div> },
  ], { initialEntries: [`/contracts/${id}`] });
  return render(<QueryClientProvider client={client}><I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty"><ToastProvider closeLabel="Закрыть"><RouterProvider router={router} /></ToastProvider></I18nProvider></QueryClientProvider>);
}

beforeEach(() => {
  vi.mocked(getContract).mockResolvedValue(contract);
  vi.mocked(updateContract).mockResolvedValue({ ...contract, contractNumber: 'CN-002', version: 5 });
  vi.mocked(transitionContract).mockResolvedValue({ ...contract, status: 'SUSPENDED', version: 5 });
});

describe('ContractDetailPage', () => {
  it('renders enriched detail and keeps custom JSON as text', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { level: 1, name: 'CN-001' })).toBeInTheDocument();
    expect(screen.getByText('Acme')).toBeInTheDocument();
    expect(screen.getByText(/<script>safe text<\/script>/)).toBeInTheDocument();
    expect(screen.queryByRole('script')).not.toBeInTheDocument();
  });

  it('updates metadata with the captured version', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByRole('heading', { name: 'CN-001' });
    await user.click(screen.getByRole('button', { name: 'Редактировать' }));
    const dialog = screen.getByRole('dialog', { name: 'Редактирование договора' });
    const number = within(dialog).getByLabelText(/Номер договора/);
    await user.clear(number); await user.type(number, 'CN-002');
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }));
    await waitFor(() => expect(updateContract).toHaveBeenCalledWith(id, expect.objectContaining({ contractNumber: 'CN-002', version: 4 })));
  });

  it('confirms lifecycle and sends the displayed version', async () => {
    const user = userEvent.setup(); renderPage();
    await screen.findByRole('heading', { name: 'CN-001' });
    await user.click(screen.getByRole('button', { name: 'Приостановить' }));
    const dialog = screen.getByRole('dialog', { name: 'Приостановить договор?' });
    await user.click(within(dialog).getByRole('button', { name: 'Приостановить' }));
    await waitFor(() => expect(transitionContract).toHaveBeenCalledWith(id, 'suspend', 4));
  });
});
