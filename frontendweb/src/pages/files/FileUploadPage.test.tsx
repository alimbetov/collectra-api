import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { FILE_CATEGORIES } from '../../entities/file/model/file.types';
import { FileUploadPage } from './FileUploadPage';

vi.mock('../../features/auth/model/auth-context', () => ({
  useAuth: () => ({ hasPermission: () => true }),
}));

vi.mock('../../entities/file/api/file.api', () => ({
  uploadFile: vi.fn(),
}));

describe('FileUploadPage', () => {
  let client: QueryClient;

  beforeEach(() => {
    client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
  });

  it('renders only categories accepted by the canonical file contract', () => {
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <FileUploadPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const options = screen.getAllByRole('option').map(option => option.textContent);
    expect(options).toEqual([...FILE_CATEGORIES]);
    expect(options).not.toContain('TEMPLATE_ASSET');
    expect(options).not.toContain('ATTACHMENT');
  });
});
