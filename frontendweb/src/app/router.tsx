import { createBrowserRouter, Navigate } from 'react-router-dom';
import { App } from './App';
import { RequireAuth } from '../features/auth/ui/RequireAuth';
import { RequirePlatformAuth } from '../features/auth/ui/RequirePlatformAuth';
import { RequirePermission } from '../features/auth/ui/RequirePermission';
import { LoginPage } from '../pages/auth/LoginPage';
import { PlatformLayout } from '../pages/platform/PlatformLayout';
import { PlatformOverviewPage } from '../pages/platform/PlatformOverviewPage';
import { PlatformTenantDetailPage } from '../pages/platform/PlatformTenantDetailPage';
import { PlatformTenantsPage } from '../pages/platform/PlatformTenantsPage';
import { PlatformUsersPage } from '../pages/platform/PlatformUsersPage';
import { PlatformUserDetailPage } from '../pages/platform/PlatformUserDetailPage';
import { PlatformAdministratorsPage } from '../pages/platform/PlatformAdministratorsPage';
import { PlatformPlaceholderPage } from '../pages/platform/PlatformPlaceholderPage';
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
import { CampaignsPage } from '../pages/campaigns/CampaignsPage';
import { CampaignDetailPage } from '../pages/campaigns/CampaignDetailPage';
import { CampaignRunPage } from '../pages/campaigns/CampaignRunPage';

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
        <PlatformLayout />
      </RequirePlatformAuth>
    ),
    children: [
      { index: true, element: <PlatformOverviewPage /> },
      { path: 'tenants', element: <PlatformTenantsPage /> },
      { path: 'tenants/:tenantId', element: <PlatformTenantDetailPage /> },
      { path: 'users', element: <PlatformUsersPage /> },
      { path: 'users/:userId', element: <PlatformUserDetailPage /> },
      { path: 'administrators', element: <PlatformAdministratorsPage /> },
      {
        path: 'analytics',
        element: <PlatformPlaceholderPage titleKey="platform.navigation.analytics" />,
      },
      {
        path: 'audit',
        element: <PlatformPlaceholderPage titleKey="platform.navigation.audit" />,
      },
      {
        path: 'operations',
        element: <PlatformPlaceholderPage titleKey="platform.navigation.operations" />,
      },
    ],
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
            <CampaignsPage />
          </RequirePermission>
        ),
      },
      {
        path: 'campaigns/:campaignId',
        element: (
          <RequirePermission permission="CAMPAIGN_READ">
            <CampaignDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'campaigns/:campaignId/runs/:runId',
        element: (
          <RequirePermission permission="CAMPAIGN_READ">
            <CampaignRunPage />
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
