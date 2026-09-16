import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../../features/auth/model/auth-context';
import { I18nProvider } from '../i18n/i18n-context';
import { ErrorBoundary } from './ErrorBoundary';

function Broken(): ReactNode {
  throw new Error('sensitive stack content');
}

describe('ErrorBoundary', () => {
  it('renders a localized safe fallback without stack content', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const client = new QueryClient();
    render(
      <QueryClientProvider client={client}>
        <AuthProvider><I18nProvider><ErrorBoundary><Broken /></ErrorBoundary></I18nProvider></AuthProvider>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Произошла непредвиденная ошибка. Попробуйте ещё раз.')).toBeInTheDocument();
    expect(screen.queryByText(/sensitive stack content/)).not.toBeInTheDocument();
  });
});
