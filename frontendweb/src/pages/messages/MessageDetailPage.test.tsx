import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getMessage } from '../../entities/message/api/message.api';
import { ApiError } from '../../shared/api/http-client';
import { MessageDetailPage } from './MessageDetailPage';

vi.mock('../../entities/message/api/message.api', () => ({ getMessage: vi.fn() }));

const campaignId = '11111111-1111-4111-8111-111111111111';
const runId = '22222222-2222-4222-8222-222222222222';
const messageId = '33333333-3333-4333-8333-333333333333';
const detail = {
  id: messageId,
  campaignId,
  campaignRunId: runId,
  customerId: '44444444-4444-4444-8444-444444444444',
  invoiceId: null,
  templateVersionId: '55555555-5555-4555-8555-555555555555',
  channel: 'EMAIL',
  maskedDestination: 'r***@example.test',
  status: 'FAILED',
  attemptCount: 2,
  resolvedLocale: 'ru',
  processingStartedAt: '2026-09-26T10:00:00Z',
  providerMessageId: null,
  lastErrorCode: 'PROVIDER_TIMEOUT',
  lastErrorSummary: 'Provider did not acknowledge request',
  nextRetryAt: null,
  sentAt: null,
  createdAt: '2026-09-26T09:59:00Z',
  attachments: [],
};

function Probe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}</output>;
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([
    {
      path: '/campaigns/:campaignId/runs/:runId/messages/:messageId',
      element: <><MessageDetailPage /><Probe /></>,
    },
    { path: '/forbidden', element: <><div>Forbidden</div><Probe /></> },
  ], { initialEntries: [`/campaigns/${campaignId}/runs/${runId}/messages/${messageId}`] });
  return render(<QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider>);
}

beforeEach(() => vi.mocked(getMessage).mockResolvedValue(detail));

describe('MessageDetailPage', () => {
  it('renders safe read-only diagnostics without inventing provider identity', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'r***@example.test' })).toBeInTheDocument();
    expect(screen.getByText('PROVIDER_TIMEOUT')).toBeInTheDocument();
    expect(screen.getByText('Provider did not acknowledge request')).toBeInTheDocument();
    expect(screen.getByText('Provider message ID').parentElement).toHaveTextContent(
      'Provider message ID—',
    );
    expect(screen.queryByRole('button', { name: /retry|cancel|повтор|отмен/i })).not.toBeInTheDocument();
  });

  it('routes authorization failures to forbidden and hides diagnostics', async () => {
    vi.mocked(getMessage).mockRejectedValueOnce(new ApiError(403, { status: 403 }));
    renderPage();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/forbidden'));
    expect(screen.queryByText('PROVIDER_TIMEOUT')).not.toBeInTheDocument();
  });
});
