import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getIntegrationSource, getIntegrationSourceReadiness } from '../../entities/integration/api/integration.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { IntegrationSourceDetailPage } from './IntegrationSourceDetailPage';

let canManage = false;

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({ hasPermission: (permission: string) =>
    permission === 'INTEGRATION_SOURCE_READ' || (permission === 'INTEGRATION_SOURCE_MANAGE' && canManage) }),
}));

vi.mock('../../entities/integration/api/integration.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/integration/api/integration.api')>(
    '../../entities/integration/api/integration.api',
  );
  return { ...actual, getIntegrationSource: vi.fn(), getIntegrationSourceReadiness: vi.fn() };
});

const id = '11111111-1111-4111-8111-111111111111';

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/integrations/sources/:sourceId', element: <IntegrationSourceDetailPage /> },
      { path: '/forbidden', element: <div>Forbidden</div> },
    ],
    { initialEntries: [`/integrations/sources/${id}`] },
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
  vi.mocked(getIntegrationSource).mockResolvedValue({
    id, name: 'CRM', code: 'CRM', status: 'DRAFT', version: 4,
    serviceClientId: null, sourceSchemaDefinitionId: null, mappingProfileDefinitionId: null,
  } as never);
  vi.mocked(getIntegrationSourceReadiness).mockResolvedValue({ ready: true, checks: [] } as never);
});

describe('IntegrationSourceDetailPage authorization affordances', () => {
  it('hides lifecycle mutations from read-only users', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { level: 1, name: 'CRM' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Активировать' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Архивировать' })).not.toBeInTheDocument();
  });

  it('shows lifecycle mutations to integration managers', async () => {
    canManage = true;
    renderPage();
    expect(await screen.findByRole('button', { name: 'Активировать' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Архивировать' })).toBeInTheDocument();
  });
});
