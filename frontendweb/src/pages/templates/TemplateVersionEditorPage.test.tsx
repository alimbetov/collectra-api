import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getBuilderCapabilities,
  getTemplateAssets,
  getTemplateFields,
  getTemplateVersion,
  updateBuilderVersion,
} from '../../entities/template/api/template.api';
import { ApiError } from '../../shared/api/http-client';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { TemplateVersionEditorPage } from './TemplateVersionEditorPage';

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({
    hasPermission: () => true,
    hasRole: () => false,
    user: null,
    status: 'authenticated',
    sessionKind: 'tenant',
  }),
}));

vi.mock('../../entities/template/api/template.api', async () => {
  const actual = await vi.importActual<typeof import('../../entities/template/api/template.api')>(
    '../../entities/template/api/template.api',
  );
  return {
    ...actual,
    getTemplateVersion: vi.fn(),
    getBuilderCapabilities: vi.fn(),
    getTemplateFields: vi.fn(),
    getTemplateAssets: vi.fn(),
    updateBuilderVersion: vi.fn(),
  };
});

const initial = {
  id: '22222222-2222-2222-2222-222222222222',
  templateId: '11111111-1111-1111-1111-111111111111',
  version: 1,
  templateVersion: 1,
  locale: 'ru',
  channel: 'EMAIL' as const,
  subject: 'Subject',
  builderJson: {
    version: '1.0' as const,
    blocks: [
      {
        type: 'richText' as const,
        props: { content: [{ type: 'text' as const, value: 'Hello' }] },
      },
    ],
  },
  content: '<div>Hello</div>',
  stylesheet: '',
  status: 'DRAFT' as const,
  createdAt: '2026-09-20T10:00:00Z',
  updatedAt: '2026-09-22T10:00:00Z',
  revision: 4,
};

beforeEach(() => {
  vi.mocked(getTemplateVersion).mockResolvedValue(initial);
  vi.mocked(getBuilderCapabilities).mockResolvedValue({
    channels: ['EMAIL', 'SMS', 'WHATSAPP', 'TELEGRAM', 'PDF'],
    eachSyntax: '{{#each items}}...{{/each}}',
    assetSyntax: '{{asset.<key>}}',
    builderSchemaVersion: '1.0',
    blockSupport: {
      EMAIL: ['header', 'footer', 'row', 'column', 'richText', 'itemsTable', 'image', 'spacer'],
      PDF: ['header', 'footer', 'row', 'column', 'richText', 'itemsTable', 'image', 'spacer'],
      SMS: ['header', 'footer', 'richText', 'spacer'],
      WHATSAPP: ['header', 'footer', 'richText', 'spacer'],
      TELEGRAM: ['header', 'footer', 'richText', 'spacer'],
    },
  });
  vi.mocked(getTemplateFields).mockResolvedValue({
    items: [],
    page: 0,
    size: 200,
    totalElements: 0,
    totalPages: 0,
  });
  vi.mocked(getTemplateAssets).mockResolvedValue({
    items: [],
    page: 0,
    size: 200,
    totalElements: 0,
    totalPages: 0,
  });
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      {
        path: '/templates/:templateId/versions/:versionId/builder',
        element: <TemplateVersionEditorPage />,
      },
      { path: '/templates/:templateId', element: <div>Template detail</div> },
    ],
    {
      initialEntries: [
        '/templates/11111111-1111-1111-1111-111111111111/versions/22222222-2222-2222-2222-222222222222/builder',
      ],
    },
  );
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <RouterProvider router={router} />
      </I18nProvider>
    </QueryClientProvider>,
  );
}

describe('TemplateVersionEditorPage', () => {
  it('preserves local draft after VERSION_CONFLICT', async () => {
    const user = userEvent.setup();
    vi.mocked(updateBuilderVersion).mockRejectedValue(
      new ApiError(409, {
        status: 409,
        code: 'VERSION_CONFLICT',
        title: 'Conflict',
      }),
    );
    vi.mocked(getTemplateVersion)
      .mockResolvedValueOnce(initial)
      .mockResolvedValue({
        ...initial,
        subject: 'Server subject',
        revision: 5,
        updatedAt: '2026-09-24T10:00:00Z',
      });

    renderPage();

    const editor = await screen.findByDisplayValue('Hello');
    await user.clear(editor);
    await user.type(editor, 'Edited local draft');
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));

    expect(await screen.findByText('Обнаружен конфликт версий')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Edited local draft')).toBeInTheDocument();

    await waitFor(() =>
      expect(getTemplateVersion).toHaveBeenCalledTimes(2),
    );
  });
});
