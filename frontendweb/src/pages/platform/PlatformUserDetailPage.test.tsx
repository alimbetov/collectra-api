import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  changePlatformMembershipStatus,
  getPlatformMembershipSessions,
  getPlatformUser,
  revokePlatformMembershipSessions,
} from '../../entities/platform/api/platform.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { PlatformUserDetailPage } from './PlatformUserDetailPage';

vi.mock('../../entities/platform/api/platform.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/platform/api/platform.api')>(
    '../../entities/platform/api/platform.api',
  );
  return {
    ...actual,
    getPlatformUser: vi.fn(),
    getPlatformMembershipSessions: vi.fn(),
    changePlatformMembershipStatus: vi.fn(),
    revokePlatformMembershipSessions: vi.fn(),
  };
});

const detail = {
  userId: '11111111-1111-1111-1111-111111111111',
  membershipId: '22222222-2222-2222-2222-222222222222',
  tenantId: '33333333-3333-3333-3333-333333333333',
  tenantSlug: 'acme',
  tenantName: 'ACME',
  email: 'operator@acme.test',
  displayName: 'Operator',
  accountStatus: 'ACTIVE' as const,
  membershipStatus: 'ACTIVE' as const,
  effectiveAccessStatus: 'ACTIVE' as const,
  roleCodes: ['TENANT_USER'],
  locale: 'ru',
  timezone: 'Asia/Almaty',
  authorizationVersion: 7,
  sessionCount: 1,
  activeSessionCount: 1,
  createdAt: '2026-09-20T10:00:00Z',
  updatedAt: '2026-09-22T10:00:00Z',
  revision: 3,
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <MemoryRouter initialEntries={[`/platform/users/${detail.userId}`]}>
          <Routes>
            <Route path="/platform/users/:userId" element={<PlatformUserDetailPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getPlatformUser).mockResolvedValue(detail);
  vi.mocked(getPlatformMembershipSessions).mockResolvedValue({
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
  });
  vi.mocked(changePlatformMembershipStatus).mockResolvedValue({
    ...detail,
    membershipStatus: 'BLOCKED',
    effectiveAccessStatus: 'BLOCKED',
    revision: 4,
  });
  vi.mocked(revokePlatformMembershipSessions).mockResolvedValue({
    ...detail,
    authorizationVersion: 8,
    activeSessionCount: 0,
  });
});

describe('PlatformUserDetailPage', () => {
  it('blocks membership with optimistic revision and reason', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Operator' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Заблокировать membership' }));

    const reason = await screen.findByRole('textbox');
    await user.type(reason, 'Security case PF3-1');
    await user.click(
      screen.getAllByRole('button', { name: 'Заблокировать membership' }).at(-1)!,
    );

    await waitFor(() =>
      expect(changePlatformMembershipStatus).toHaveBeenCalledWith(detail.membershipId, {
        active: false,
        revision: 3,
        reason: 'Security case PF3-1',
      }),
    );
  });

  it('requires a reason before revoking all sessions', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Отозвать все сессии' }));
    await user.click(screen.getAllByRole('button', { name: 'Отозвать все сессии' }).at(-1)!);

    expect(await screen.findByText('Причина обязательна.')).toBeInTheDocument();
    expect(revokePlatformMembershipSessions).not.toHaveBeenCalled();
  });
});
