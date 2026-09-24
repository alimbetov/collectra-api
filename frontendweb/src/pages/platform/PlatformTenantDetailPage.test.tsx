import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  changePlatformTenantStatus,
  getPlatformTenant,
} from '../../entities/platform/api/platform.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PlatformTenantDetailPage } from './PlatformTenantDetailPage';

vi.mock('../../entities/platform/api/platform.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/platform/api/platform.api')>(
    '../../entities/platform/api/platform.api',
  );
  return {
    ...actual,
    getPlatformTenant: vi.fn(),
    changePlatformTenantStatus: vi.fn(),
  };
});

const tenant = {
  id: '4f80b4f3-5d86-4a74-b105-b0068df1b02f',
  slug: 'acme',
  name: 'ACME',
  status: 'ACTIVE' as const,
  createdAt: '2026-09-20T10:00:00Z',
  updatedAt: '2026-09-22T10:00:00Z',
  revision: 4,
  activeUsers: 12,
  blockedUsers: 2,
  customers: 300,
  campaigns: 18,
  messages: 1200,
  generatedDocuments: 400,
  files: 35,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <MemoryRouter initialEntries={[`/platform/tenants/${tenant.id}`]}>
          <Routes>
            <Route path="/platform/tenants/:tenantId" element={<PlatformTenantDetailPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getPlatformTenant).mockResolvedValue(tenant);
  vi.mocked(changePlatformTenantStatus).mockResolvedValue({
    ...tenant,
    status: 'BLOCKED',
    revision: 5,
    updatedAt: '2026-09-23T10:00:00Z',
  });
});

describe('PlatformTenantDetailPage', () => {
  it('blocks a tenant with reason and optimistic revision', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'ACME' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Заблокировать tenant' }));

    const reason = await screen.findByPlaceholderText(
      'Укажите операционную или security-причину',
    );
    await user.type(reason, 'Security incident INC-123');
    await user.click(screen.getAllByRole('button', { name: 'Заблокировать tenant' }).at(-1)!);

    await waitFor(() =>
      expect(changePlatformTenantStatus).toHaveBeenCalledWith(tenant.id, {
        active: false,
        revision: 4,
        reason: 'Security incident INC-123',
      }),
    );
  });

  it('requires a reason before lifecycle mutation', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Заблокировать tenant' }));
    await user.click(screen.getAllByRole('button', { name: 'Заблокировать tenant' }).at(-1)!);

    expect(await screen.findByText('Причина обязательна.')).toBeInTheDocument();
    expect(changePlatformTenantStatus).not.toHaveBeenCalled();
  });
});
