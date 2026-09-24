import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  archiveTemplateAsset,
  getTemplateAssets,
  registerTemplateAsset,
  uploadTemplateAssetFile,
} from '../../entities/template/api/template.api';
import { I18nProvider } from '../../shared/i18n/i18n-context';
import { TemplateAssetsPage } from './TemplateAssetsPage';

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
    getTemplateAssets: vi.fn(),
    uploadTemplateAssetFile: vi.fn(),
    registerTemplateAsset: vi.fn(),
    archiveTemplateAsset: vi.fn(),
  };
});

beforeEach(() => {
  vi.mocked(getTemplateAssets).mockResolvedValue({
    items: [
      {
        id: '11111111-1111-1111-1111-111111111111',
        key: 'logo',
        fileId: '22222222-2222-2222-2222-222222222222',
        altText: 'Logo',
        placeholder: '{{asset.logo}}',
      },
    ],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
  });

  vi.mocked(uploadTemplateAssetFile).mockResolvedValue({
    fileId: '33333333-3333-3333-3333-333333333333',
    tenantId: '44444444-4444-4444-4444-444444444444',
    projectId: null,
    category: 'ASSET',
    originalFilename: 'new-logo.png',
    contentType: 'image/png',
    sizeBytes: 128,
    checksumSha256: null,
    status: 'READY',
    createdAt: '2026-09-24T10:00:00Z',
    expiresAt: null,
    deletedAt: null,
  });

  vi.mocked(registerTemplateAsset).mockResolvedValue({
    id: '55555555-5555-5555-5555-555555555555',
    key: 'new_logo',
    fileId: '33333333-3333-3333-3333-333333333333',
    altText: 'New logo',
    placeholder: '{{asset.new_logo}}',
  });

  vi.mocked(archiveTemplateAsset).mockResolvedValue(undefined);
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <I18nProvider requestedLocale="ru" requestedTimeZone="UTC">
        <MemoryRouter initialEntries={['/templates/assets']}>
          <TemplateAssetsPage />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

describe('TemplateAssetsPage', () => {
  it('uploads file then registers tenant asset', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('{{asset.logo}}')).toBeInTheDocument();

    const file = new File(['png'], 'new-logo.png', { type: 'image/png' });
    await user.upload(screen.getByLabelText('Файл'), file);
    await user.type(screen.getByLabelText('Ключ asset'), 'new_logo');
    await user.type(screen.getByLabelText('Alt-текст'), 'New logo');
    await user.click(screen.getByRole('button', { name: 'Загрузить asset' }));

    await waitFor(() => expect(uploadTemplateAssetFile).toHaveBeenCalledWith(file));
    expect(registerTemplateAsset).toHaveBeenCalledWith({
      key: 'new_logo',
      fileId: '33333333-3333-3333-3333-333333333333',
      altText: 'New logo',
    });
  });

  it('blocks unsupported and oversized images before upload', async () => {
    const user = userEvent.setup();
    renderPage();

    const wrong = new File(['x'], 'vector.svg', { type: 'image/svg+xml' });
    await user.upload(screen.getByLabelText('Файл'), wrong, { applyAccept: false });
    await user.type(screen.getByLabelText('Ключ asset'), 'vector');
    await user.click(screen.getByRole('button', { name: 'Загрузить asset' }));

    expect(await screen.findByText('Поддерживаются только PNG, JPEG и GIF.')).toBeInTheDocument();
    expect(uploadTemplateAssetFile).not.toHaveBeenCalled();
  });
});
