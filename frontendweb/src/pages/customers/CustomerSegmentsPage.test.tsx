import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createSegment, getSegment, getSegments, updateSegment } from '../../entities/customer/api/customer.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { ToastProvider } from '../../shared/ui';
import { CustomerSegmentsPage } from './CustomerSegmentsPage';

vi.mock('../../entities/customer/api/customer.api', () => ({
  getSegments: vi.fn(),
  getSegment: vi.fn(),
  createSegment: vi.fn(),
  updateSegment: vi.fn(),
}));

const segmentId = '33333333-3333-4333-8333-333333333333';
const segment = {
  id: segmentId,
  code: 'VIP',
  name: 'VIP customers',
  description: 'Priority',
  active: true,
  createdAt: '2026-09-17T10:00:00Z',
  updatedAt: '2026-09-18T10:00:00Z',
  version: 4,
};

function page(items = [segment]) {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0, hasNext: false };
}

function renderPage(entry: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter([
    { path: '/customers/segments', element: <CustomerSegmentsPage /> },
    { path: '/customers/segments/:segmentId', element: <CustomerSegmentsPage /> },
    { path: '/customers', element: <div>Customers</div> },
    { path: '/forbidden', element: <div>Forbidden</div> },
  ], { initialEntries: [entry] });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="Asia/Almaty">
        <ToastProvider closeLabel="Закрыть"><RouterProvider router={router} /></ToastProvider>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(getSegments).mockResolvedValue(page());
  vi.mocked(getSegment).mockResolvedValue(segment);
  vi.mocked(createSegment).mockResolvedValue(segment);
  vi.mocked(updateSegment).mockResolvedValue({ ...segment, name: 'Updated', version: 5 });
});

describe('CustomerSegmentsPage', () => {
  it('loads the registry from canonical URL-owned filters', async () => {
    renderPage('/customers/segments?search=vip&active=true&page=2&size=50&sort=code%2Cdesc');

    expect(await screen.findByText('VIP customers')).toBeInTheDocument();
    expect(getSegments).toHaveBeenCalledWith({ search: 'vip', active: true, page: 2, size: 50, sort: 'code,desc' });
  });

  it('opens a direct detail URL and submits the captured segment version', async () => {
    const user = userEvent.setup();
    renderPage(`/customers/segments/${segmentId}`);

    const dialog = await screen.findByRole('dialog', { name: 'Редактирование сегмента' });
    const name = within(dialog).getByLabelText(/Название/);
    await user.clear(name);
    await user.type(name, 'Updated');
    await user.click(within(dialog).getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => expect(updateSegment).toHaveBeenCalledWith(segmentId, {
      name: 'Updated', description: 'Priority', active: true, version: 4,
    }));
  });
});
