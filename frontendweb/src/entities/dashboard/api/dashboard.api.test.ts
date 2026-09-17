import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import {
  getDashboardCollections,
  getDashboardDelivery,
  getDashboardReceivables,
  getDashboardSummary,
} from './dashboard.api';

const snapshot = { asOf: '2026-09-17T03:00:00Z', businessDate: '2026-09-17' };
const server = setupServer(
  http.get('/api/v1/dashboard/summary', () =>
    HttpResponse.json({ ...snapshot, customers: 1, activeContracts: 2, openCollectionCases: 3, activeCampaigns: 4, outstandingByCurrency: [{ currency: 'KZT', amount: '999999999999999.9999' }] }),
  ),
  http.get('/api/v1/dashboard/receivables', () =>
    HttpResponse.json({ ...snapshot, currencies: [] }),
  ),
  http.get('/api/v1/dashboard/delivery', () =>
    HttpResponse.json({ ...snapshot, recipients: 10, sent: 8, failed: 1, skipped: 0, retries: 1 }),
  ),
  http.get('/api/v1/dashboard/collections', () =>
    HttpResponse.json({ ...snapshot, activeCases: 2, overdueActions: 1, activePromisesDue: 3, activePromisesOverdue: 1, brokenPromises: 0, openDisputes: 0 }),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('dashboard API contract', () => {
  it('loads all four projections and preserves Decimal String money', async () => {
    const [summary, receivables, delivery, collections] = await Promise.all([
      getDashboardSummary(),
      getDashboardReceivables(),
      getDashboardDelivery(),
      getDashboardCollections(),
    ]);

    expect(summary.outstandingByCurrency[0].amount).toBe('999999999999999.9999');
    expect(receivables.currencies).toEqual([]);
    expect(delivery.sent).toBe(8);
    expect(collections.overdueActions).toBe(1);
  });
});
