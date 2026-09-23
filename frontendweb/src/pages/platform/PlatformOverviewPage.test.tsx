import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PlatformOverviewPage } from './PlatformOverviewPage';
import { getPlatformOverview } from '../../entities/platform/api/platform.api';

vi.mock('../../entities/platform/api/platform.api', () => ({
  getPlatformOverview: vi.fn(),
}));

const overview = {
  generatedAt: '2026-09-23T00:00:00Z',
  periodFrom: '2026-08-24T00:00:00Z',
  tenantsTotal: 10,
  tenantsActive: 8,
  tenantsBlocked: 2,
  usersTotal: 120,
  usersActive: 100,
  usersBlocked: 20,
  campaignsLast30Days: 42,
  messagesLast30Days: 1500,
  sentLast30Days: 1400,
  failedLast30Days: 50,
  retryWaitCurrent: 10,
  unknownCurrent: 5,
  channels: [
    { channel: 'EMAIL', messages: 1200 },
    { channel: 'SMS', messages: 300 },
  ],
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <PlatformOverviewPage />
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getPlatformOverview).mockResolvedValue(overview);
});

describe('PlatformOverviewPage', () => {
  it('renders platform KPIs and channel summary', async () => {
    renderPage();

    expect(await screen.findByText('Обзор платформы')).toBeInTheDocument();
    expect(await screen.findByText('Всего тенантов')).toBeInTheDocument();
    expect(screen.getByText('1,500')).toBeInTheDocument();
    expect(screen.getByText('EMAIL')).toBeInTheDocument();
    expect(screen.getByText('SMS')).toBeInTheDocument();
    expect(screen.getByText(/Данные сформированы/)).toBeInTheDocument();
  });

  it('shows a safe error state', async () => {
    vi.mocked(getPlatformOverview).mockRejectedValue(new Error('secret backend detail'));
    renderPage();

    expect(await screen.findByText('Не удалось выполнить действие')).toBeInTheDocument();
    expect(screen.queryByText('secret backend detail')).not.toBeInTheDocument();
  });
});
