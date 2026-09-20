import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createContract, getContracts } from '../../entities/contract/api/contract.api';
import { getCustomers } from '../../entities/customer/api/customer.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { ToastProvider } from '../../shared/ui';
import { ContractsPage } from './ContractsPage';

vi.mock('../../entities/contract/api/contract.api', () => ({ getContracts: vi.fn(), createContract: vi.fn() }));
vi.mock('../../entities/customer/api/customer.api', () => ({ getCustomers: vi.fn() }));

function renderPage(entry: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([{ path: '/contracts', element: <ContractsPage /> }, { path: '/contracts/:contractId', element: <div>Detail</div> }, { path: '/forbidden', element: <div>Forbidden</div> }], { initialEntries: [entry] });
  return render(<QueryClientProvider client={client}><I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty"><ToastProvider closeLabel="Закрыть"><RouterProvider router={router} /></ToastProvider></I18nProvider></QueryClientProvider>);
}

beforeEach(() => {
  vi.mocked(getContracts).mockResolvedValue({ items: [{ id: '1', customerId: '2', customerExternalId: 'C-1', customerDisplayName: 'Acme', externalId: 'EXT', contractNumber: 'CN-1', status: 'ACTIVE', validFrom: '2026-01-01', validTo: null, renewalDate: null, createdAt: '', updatedAt: '', version: 0 }], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false });
  vi.mocked(getCustomers).mockResolvedValue({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
  vi.mocked(createContract).mockRejectedValue(new Error('unused'));
});

describe('ContractsPage', () => {
  it('loads the canonical URL filters with explicit paging', async () => {
    renderPage('/contracts?status=ACTIVE&page=2&size=50&sort=contractNumber%2Casc');
    expect(await screen.findByText('CN-1')).toBeInTheDocument();
    expect(getContracts).toHaveBeenCalledWith({ status: 'ACTIVE', page: 2, size: 50, sort: 'contractNumber,asc' });
  });

  it('does not request an inverted validity range', () => {
    renderPage('/contracts?validFrom=2026-05-01&validTo=2026-04-01');
    expect(screen.getByText('Начальная дата не может быть позже конечной.')).toBeInTheDocument();
    expect(getContracts).not.toHaveBeenCalled();
  });
});
