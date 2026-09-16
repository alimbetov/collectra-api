import { createBrowserRouter } from 'react-router-dom';
import { App } from './App';
import { RequireAuth } from '../features/auth/ui/RequireAuth';
import { RequirePermission } from '../features/auth/ui/RequirePermission';
import { LoginPage } from '../pages/auth/LoginPage';
import { ForbiddenPage } from '../pages/system/ForbiddenPage';
import { NotFoundPage } from '../pages/system/NotFoundPage';
import { useI18n } from '../shared/i18n/i18n-context';
import type { MessageKey } from '../shared/i18n/messages';

function PlaceholderPage({ titleKey }: { titleKey: MessageKey }) {
  const { t } = useI18n();
  return (
    <div className="placeholder-page">
      <p className="eyebrow">{t('placeholder.eyebrow')}</p>
      <h1>{t(titleKey)}</h1>
      <p>{t('placeholder.description')}</p>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <App />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <PlaceholderPage titleKey="navigation.dashboard" /> },
      { path: 'customers', element: <PlaceholderPage titleKey="navigation.customers" /> },
      { path: 'receivables', element: <PlaceholderPage titleKey="navigation.receivables" /> },
      { path: 'collections', element: <PlaceholderPage titleKey="navigation.collections" /> },
      {
        path: 'campaigns',
        element: (
          <RequirePermission permission="CAMPAIGN_READ">
            <PlaceholderPage titleKey="navigation.campaigns" />
          </RequirePermission>
        ),
      },
      {
        path: 'templates',
        element: (
          <RequirePermission permission="TEMPLATE_READ">
            <PlaceholderPage titleKey="navigation.templates" />
          </RequirePermission>
        ),
      },
      {
        path: 'imports',
        element: (
          <RequirePermission permission="DOCUMENT_READ">
            <PlaceholderPage titleKey="navigation.imports" />
          </RequirePermission>
        ),
      },
      {
        path: 'files',
        element: (
          <RequirePermission permission="FILE_READ">
            <PlaceholderPage titleKey="navigation.files" />
          </RequirePermission>
        ),
      },
      { path: 'forbidden', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
