import { createBrowserRouter, Navigate } from 'react-router-dom';
import { App } from './App';
import { RequireAuth } from '../features/auth/ui/RequireAuth';
import { RequirePlatformAuth } from '../features/auth/ui/RequirePlatformAuth';
import { RequirePermission } from '../features/auth/ui/RequirePermission';
import { LoginPage } from '../pages/auth/LoginPage';
import { PlatformPage } from '../pages/platform/PlatformPage';
import { ForbiddenPage } from '../pages/system/ForbiddenPage';
import { NotFoundPage } from '../pages/system/NotFoundPage';
import { useI18n } from '../shared/i18n/i18n-context';
import type { MessageKey } from '../shared/i18n/messages';
import { RouteErrorPage } from '../pages/system/RouteErrorPage';
import { DashboardPage } from '../pages/dashboard/DashboardPage';
import { CustomersPage } from '../pages/customers/CustomersPage';
import { CustomerDetailPage } from '../pages/customers/CustomerDetailPage';
import { CustomerSegmentsPage } from '../pages/customers/CustomerSegmentsPage';
import { ContractsPage } from '../pages/contracts/ContractsPage';
import { ContractDetailPage } from '../pages/contracts/ContractDetailPage';

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
    errorElement: <RouteErrorPage />,
  },
  {
    path: '/platform',
    errorElement: <RouteErrorPage />,
    element: (
      <RequirePlatformAuth>
        <PlatformPage />
      </RequirePlatformAuth>
    ),
  },
  {
    path: '/',
    errorElement: <RouteErrorPage />,
    element: (
      <RequireAuth>
        <App />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'customers', element: <CustomersPage /> },
      { path: 'customers/segments', element: <CustomerSegmentsPage /> },
      { path: 'customers/segments/:segmentId', element: <CustomerSegmentsPage /> },
      { path: 'customers/:customerId', element: <CustomerDetailPage tab="overview" /> },
      { path: 'customers/:customerId/contacts', element: <CustomerDetailPage tab="contacts" /> },
      { path: 'customers/:customerId/contracts', element: <CustomerDetailPage tab="contracts" /> },
      { path: 'customers/:customerId/overview', element: <Navigate to=".." relative="path" replace /> },
      { path: 'receivables', element: <PlaceholderPage titleKey="navigation.receivables" /> },
      { path: 'contracts', element: <ContractsPage /> },
      { path: 'contracts/:contractId', element: <ContractDetailPage /> },
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
