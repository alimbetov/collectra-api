import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { queryClient } from './app/query-client';
import { router } from './app/router';
import { AuthProvider } from './features/auth/model/auth-context';
import { I18nProvider } from './shared/i18n/i18n-context';
import { ErrorBoundary } from './shared/errors/ErrorBoundary';
import './styles.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element was not found');
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <I18nProvider>
          <ErrorBoundary>
            <RouterProvider router={router} />
          </ErrorBoundary>
        </I18nProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
