import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/http-client';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { DashboardPage } from './DashboardPage';
import {
  getDashboardCollections,
  getDashboardDelivery,
  getDashboardReceivables,
  getDashboardSummary,
} from '../../entities/dashboard/api/dashboard.api';

vi.mock('../../entities/dashboard/api/dashboard.api', () => ({
  getDashboardSummary: vi.fn(),
  getDashboardReceivables: vi.fn(),
  getDashboardDelivery: vi.fn(),
  getDashboardCollections: vi.fn(),
}));

const summary = {
  asOf: '2026-09-17T03:00:00Z',
  businessDate: '2026-09-17',
  customers: 4,
  activeContracts: 3,
  openCollectionCases: 2,
  activeCampaigns: 1,
  outstandingByCurrency: [{ currency: 'KZT', amount: '1000.25' }],
};
const receivables = {
  asOf: '2026-09-17T03:01:00Z',
  businessDate: '2026-09-17',
  currencies: [{ currency: 'KZT', outstanding: '1000.25', dueToday: 1, dueSoon: 2, aging: { current: '500', days1To30: '300', days31To60: '100', days61To90: '50', days90Plus: '50.25' } }],
};
const delivery = {
  asOf: '2026-09-17T03:02:00Z',
  businessDate: '2026-09-17',
  recipients: 10,
  sent: 8,
  failed: 1,
  skipped: 0,
  retries: 1,
};
const collections = {
  asOf: '2026-09-17T03:03:00Z',
  businessDate: '2026-09-17',
  activeCases: 2,
  overdueActions: 1,
  activePromisesDue: 1,
  activePromisesOverdue: 1,
  brokenPromises: 1,
  openDisputes: 1,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <MemoryRouter>
          <DashboardPage />
          <LocationProbe />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

beforeEach(() => {
  vi.mocked(getDashboardSummary).mockResolvedValue(summary);
  vi.mocked(getDashboardReceivables).mockResolvedValue(receivables);
  vi.mocked(getDashboardDelivery).mockResolvedValue(delivery);
  vi.mocked(getDashboardCollections).mockResolvedValue(collections);
});

describe('DashboardPage', () => {
  it('renders independent projections, freshness and the supported overdue filter link', async () => {
    renderPage();
    expect(await screen.findByText('Накопительные показатели по всем запускам tenant.')).toBeInTheDocument();
    expect(screen.getAllByText(/Данные на:/)).toHaveLength(4);
    expect(screen.getByRole('link', { name: 'Открыть просроченные' })).toHaveAttribute(
      'href',
      '/receivables?view=invoices&overdue=true',
    );
    expect(screen.getByText(/500,25/)).toBeInTheDocument();
    expect(screen.getByText('Просроченные действия')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Просроченные действия' })).not.toBeInTheDocument();
  });

  it('keeps successful cards usable when one projection fails', async () => {
    vi.mocked(getDashboardSummary).mockRejectedValue(
      new ApiError(500, { status: 500, detail: 'secret server detail', correlationId: 'safe-id' }),
    );
    renderPage();
    expect(await screen.findByText('Не удалось выполнить действие')).toBeInTheDocument();
    expect(screen.queryByText('secret server detail')).not.toBeInTheDocument();
    expect(screen.getByText('Получатели')).toBeInTheDocument();
    expect(screen.getByText('Активные дела')).toBeInTheDocument();
  });

  it('renders successful empty projections only after loading finishes', async () => {
    vi.mocked(getDashboardSummary).mockResolvedValue({
      ...summary,
      customers: 0,
      activeContracts: 0,
      openCollectionCases: 0,
      activeCampaigns: 0,
      outstandingByCurrency: [],
    });
    vi.mocked(getDashboardReceivables).mockResolvedValue({ ...receivables, currencies: [] });
    vi.mocked(getDashboardDelivery).mockResolvedValue({ ...delivery, recipients: 0, sent: 0, failed: 0, skipped: 0, retries: 0 });
    vi.mocked(getDashboardCollections).mockResolvedValue({ ...collections, activeCases: 0, overdueActions: 0, activePromisesDue: 0, activePromisesOverdue: 0, brokenPromises: 0, openDisputes: 0 });
    renderPage();
    expect(await screen.findByText('Показателей пока нет')).toBeInTheDocument();
    expect(screen.getByText('Активной задолженности нет')).toBeInTheDocument();
    expect(screen.getByText('Доставок пока нет')).toBeInTheDocument();
    expect(screen.getByText('Активной работы по взысканию нет')).toBeInTheDocument();
  });

  it('uses the shared forbidden route for an authorization failure', async () => {
    vi.mocked(getDashboardCollections).mockRejectedValue(new ApiError(403, { status: 403 }));
    renderPage();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'));
  });
});
