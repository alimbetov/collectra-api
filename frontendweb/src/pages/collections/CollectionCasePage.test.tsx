import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getActions,
  getCollectionCase,
  getDisputes,
  getPromises,
  getTimeline,
} from '../../entities/collection/api/collection.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { CollectionCasePage } from './CollectionCasePage';

let canManage = false;

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({
    hasPermission: (permission: string) =>
      permission === 'COLLECTION_READ' || (permission === 'COLLECTION_MANAGE' && canManage),
    hasRole: () => false,
    user: null,
    status: 'authenticated',
    sessionKind: 'tenant',
  }),
}));

vi.mock('../../entities/collection/api/collection.api', async () => {
  const actual = await vi.importActual<
    typeof import('../../entities/collection/api/collection.api')
  >('../../entities/collection/api/collection.api');
  return {
    ...actual,
    getCollectionCase: vi.fn(),
    getPromises: vi.fn(),
    getDisputes: vi.fn(),
    getActions: vi.fn(),
    getTimeline: vi.fn(),
  };
});

const caseId = '11111111-1111-4111-8111-111111111111';
const customerId = '22222222-2222-4222-8222-222222222222';
const invoiceId = '33333333-3333-4333-8333-333333333333';
const emptyPage = { items: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/collections/:caseId', element: <CollectionCasePage /> },
      { path: '/forbidden', element: <div>Forbidden</div> },
    ],
    { initialEntries: [`/collections/${caseId}`] },
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
  vi.mocked(getCollectionCase).mockResolvedValue({
    id: caseId,
    customerId,
    invoiceId,
    status: 'OPEN',
    priority: 'HIGH',
    assignedTo: null,
    openedAt: '2026-09-20T10:00:00Z',
    closedAt: null,
    closeReason: null,
    version: 3,
  });
  vi.mocked(getPromises).mockResolvedValue(emptyPage);
  vi.mocked(getDisputes).mockResolvedValue(emptyPage);
  vi.mocked(getActions).mockResolvedValue(emptyPage);
  vi.mocked(getTimeline).mockResolvedValue({
    ...emptyPage,
    items: [
      {
        eventId: '44444444-4444-4444-8444-444444444444',
        eventType: 'CASE_OPENED',
        entityType: 'COLLECTION_CASE',
        entityId: caseId,
        eventAt: '2026-09-20T10:00:00Z',
        actor: 'system',
        summary: 'Case opened',
      },
    ],
    totalElements: 1,
    totalPages: 1,
  });
});

describe('CollectionCasePage', () => {
  it('loads the authoritative case children and canonical deep links', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Collection case' })).toBeInTheDocument();
    expect(screen.getByText('Priority: HIGH')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Customer' })).toHaveAttribute(
      'href',
      `/customers/${customerId}`,
    );
    expect(screen.getByRole('link', { name: 'Invoice' })).toHaveAttribute(
      'href',
      `/receivables/invoices/${invoiceId}`,
    );
    expect(await screen.findByText(/CASE_OPENED · Case opened/)).toBeInTheDocument();

    expect(getCollectionCase).toHaveBeenCalledWith(caseId);
    expect(getPromises).toHaveBeenCalledWith(caseId);
    expect(getDisputes).toHaveBeenCalledWith(caseId);
    expect(getActions).toHaveBeenCalledWith(caseId);
    expect(getTimeline).toHaveBeenCalledWith(caseId);
  });

  it('shows collection commands only to a managing actor', async () => {
    canManage = true;
    renderPage();

    expect(await screen.findByText('Priority: HIGH')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Start' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add promise' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open dispute' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add action' })).toBeInTheDocument();
  });

  it('keeps collection mutations hidden for a read-only actor', async () => {
    renderPage();

    expect(await screen.findByText('Priority: HIGH')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Start' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add promise' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Open dispute' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add action' })).not.toBeInTheDocument();
  });
});
