import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { queryClient } from './app/query-client';
import { router } from './app/router';
import { AuthProvider } from './features/auth/model/auth-context';
import { ErrorBoundary } from './shared/errors/ErrorBoundary';
import { FeedbackProvider } from './app/FeedbackProvider';
import { AppI18nProvider } from './app/AppI18nProvider';
import './styles.css';

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element was not found');
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AppI18nProvider>
          <FeedbackProvider>
            <ErrorBoundary>
              <RouterProvider router={router} />
            </ErrorBoundary>
          </FeedbackProvider>
        </AppI18nProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
