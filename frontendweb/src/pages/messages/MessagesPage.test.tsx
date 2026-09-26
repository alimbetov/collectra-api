import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMessages } from '../../entities/message/api/message.api';
import { ApiError } from '../../shared/api/http-client';
import { MessagesPage } from './MessagesPage';

vi.mock('../../entities/message/api/message.api', () => ({ getMessages: vi.fn() }));

const campaignId = '11111111-1111-4111-8111-111111111111';
const runId = '22222222-2222-4222-8222-222222222222';
const messageId = '33333333-3333-4333-8333-333333333333';
const customerId = '44444444-4444-4444-8444-444444444444';
const page = {
  content: [{
    id: messageId,
    campaignRunId: runId,
    customerId,
    channel: 'EMAIL',
    maskedDestination: 'r***@example.test',
    status: 'SENT',
    attemptCount: 1,
    nextRetryAt: null,
    sentAt: '2026-09-26T10:00:00Z',
    createdAt: '2026-09-26T09:59:00Z',
  }],
  page: 0,
  size: 50,
  hasNext: false,
};

function Probe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderPage(entry = `/campaigns/${campaignId}/runs/${runId}/messages`) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([
    { path: '/campaigns/:campaignId/runs/:runId/messages', element: <><MessagesPage /><Probe /></> },
    { path: '/forbidden', element: <><div>Forbidden</div><Probe /></> },
  ], { initialEntries: [entry] });
  return render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>);
}

beforeEach(() => vi.mocked(getMessages).mockResolvedValue(page));

describe('MessagesPage', () => {
  it('renders only the masked destination and canonical deep links', async () => {
    renderPage();
    expect(await screen.findByText('r***@example.test')).toBeInTheDocument();
    expect(screen.queryByText('raw@example.test')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'r***@example.test' }))
      .toHaveAttribute('href', messageId);
    expect(screen.getByRole('link', { name: customerId }))
      .toHaveAttribute('href', `/customers/${customerId}`);
  });

  it('forwards URL filters to the tenant-scoped server query', async () => {
    renderPage(`/campaigns/${campaignId}/runs/${runId}/messages?status=FAILED&channel=EMAIL&customerId=${customerId}&page=2`);
    await screen.findByText('r***@example.test');
    expect(getMessages).toHaveBeenCalledWith(campaignId, runId, {
      status: 'FAILED',
      channel: 'EMAIL',
      customerId,
      page: 2,
      size: 50,
    });
  });

  it('routes authorization failures to forbidden without rendering data', async () => {
    vi.mocked(getMessages).mockRejectedValueOnce(new ApiError(403, { status: 403 }));
    renderPage();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'));
    expect(screen.queryByText('r***@example.test')).not.toBeInTheDocument();
  });
});
