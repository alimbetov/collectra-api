import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPayment, getPaymentAllocations } from '../../entities/receivable/api/receivable.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PaymentDetailPage } from './PaymentDetailPage';

let canManage = false;

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({
    hasPermission: (permission: string) =>
      permission === 'RECEIVABLE_READ' || (permission === 'RECEIVABLE_MANAGE' && canManage),
  }),
}));

vi.mock('../../entities/receivable/api/receivable.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/receivable/api/receivable.api')>(
    '../../entities/receivable/api/receivable.api',
  );
  return {
    ...actual,
    getPayment: vi.fn(),
    getPaymentAllocations: vi.fn(),
  };
});

const paymentId = '11111111-1111-4111-8111-111111111111';

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/receivables/payments/:paymentId', element: <PaymentDetailPage /> },
      { path: '/forbidden', element: <div>Forbidden</div> },
    ],
    { initialEntries: [`/receivables/payments/${paymentId}`] },
  );
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty">
        <RouterProvider router={router} />
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  canManage = false;
  vi.mocked(getPayment).mockResolvedValue({
    id: paymentId,
    customerId: '22222222-2222-4222-8222-222222222222',
    externalId: 'PAY-1',
    paymentDate: '2026-09-30',
    amount: '100.00',
    currency: 'KZT',
    paymentReference: 'REF-1',
    createdAt: '2026-09-30T00:00:00Z',
    updatedAt: '2026-09-30T00:00:00Z',
  } as never);
  vi.mocked(getPaymentAllocations).mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  } as never);
});

describe('PaymentDetailPage authorization affordances', () => {
  it('keeps allocation and reversal controls hidden for RECEIVABLE_READ-only users', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { level: 1, name: /100.00 KZT/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Распределить' })).not.toBeInTheDocument();
    expect(screen.queryByText('Причина reversal')).not.toBeInTheDocument();
  });

  it('shows allocation controls to RECEIVABLE_MANAGE users', async () => {
    canManage = true;
    renderPage();
    expect(await screen.findByRole('button', { name: 'Распределить' })).toBeInTheDocument();
    expect(screen.getByText('Причина reversal')).toBeInTheDocument();
  });
});
