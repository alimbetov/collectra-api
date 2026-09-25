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
import { TemplatesPage } from '../pages/templates/TemplatesPage';
import { TemplateCreatePage } from '../pages/templates/TemplateCreatePage';
import { TemplateDetailPage } from '../pages/templates/TemplateDetailPage';
import { TemplateVersionEditorPage } from '../pages/templates/TemplateVersionEditorPage';
import { TemplateAssetsPage } from '../pages/templates/TemplateAssetsPage';
import { IntegrationsPage } from '../pages/integrations/IntegrationsPage';
import { ServiceClientsPage } from '../pages/integrations/ServiceClientsPage';
import { ServiceClientCreatePage } from '../pages/integrations/ServiceClientCreatePage';
import { ServiceClientDetailPage } from '../pages/integrations/ServiceClientDetailPage';
import { IntegrationSourcesPage } from '../pages/integrations/IntegrationSourcesPage';
import { IntegrationSourceCreatePage } from '../pages/integrations/IntegrationSourceCreatePage';
import { IntegrationSourceDetailPage } from '../pages/integrations/IntegrationSourceDetailPage';
import { SourceSchemasPage } from '../pages/integrations/SourceSchemasPage';
import { MappingProfilesPage } from '../pages/integrations/MappingProfilesPage';
import { SourceSchemaDetailPage } from '../pages/integrations/SourceSchemaDetailPage';
import { MappingProfileDetailPage } from '../pages/integrations/MappingProfileDetailPage';
import { ImportsPage } from '../pages/imports/ImportsPage';
import { ImportCreatePage } from '../pages/imports/ImportCreatePage';
import { ImportDetailPage } from '../pages/imports/ImportDetailPage';
import { ImportErrorsPage } from '../pages/imports/ImportErrorsPage';
import { ReceivablesPage } from '../pages/receivables/ReceivablesPage';
import { InvoiceDetailPage } from '../pages/receivables/InvoiceDetailPage';
import { InvoiceCreatePage } from '../pages/receivables/InvoiceCreatePage';

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
      { path: 'receivables', element: <ReceivablesPage /> },
      { path: 'receivables/invoices/new', element: <InvoiceCreatePage /> },
      { path: 'receivables/invoices/:invoiceId', element: <InvoiceDetailPage /> },
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
            <TemplatesPage />
          </RequirePermission>
        ),
      },
      {
        path: 'templates/new',
        element: (
          <RequirePermission permission="TEMPLATE_MANAGE">
            <TemplateCreatePage />
          </RequirePermission>
        ),
      },
      {
        path: 'templates/assets',
        element: (
          <RequirePermission permission="TEMPLATE_READ">
            <TemplateAssetsPage />
          </RequirePermission>
        ),
      },
      {
        path: 'templates/:templateId',
        element: (
          <RequirePermission permission="TEMPLATE_READ">
            <TemplateDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'templates/:templateId/versions/:versionId',
        element: (
          <RequirePermission permission="TEMPLATE_READ">
            <TemplateVersionEditorPage />
          </RequirePermission>
        ),
      },
      {
        path: 'templates/:templateId/versions/:versionId/builder',
        element: (
          <RequirePermission permission="TEMPLATE_READ">
            <TemplateVersionEditorPage />
          </RequirePermission>
        ),
      },
      {
        path: 'integrations',
        element: <IntegrationsPage />,
      },
      {
        path: 'integrations/source-schemas',
        element: <RequirePermission permission="SOURCE_SCHEMA_READ"><SourceSchemasPage /></RequirePermission>,
      },
      {
        path: 'integrations/source-schemas/:schemaId',
        element: <RequirePermission permission="SOURCE_SCHEMA_READ"><SourceSchemaDetailPage /></RequirePermission>,
      },
      {
        path: 'integrations/mapping-profiles/:profileId',
        element: <RequirePermission permission="MAPPING_PROFILE_READ"><MappingProfileDetailPage /></RequirePermission>,
      },
      {
        path: 'integrations/mapping-profiles',
        element: <RequirePermission permission="MAPPING_PROFILE_READ"><MappingProfilesPage /></RequirePermission>,
      },
      {
        path: 'integrations/sources',
        element: <RequirePermission permission="INTEGRATION_SOURCE_READ"><IntegrationSourcesPage /></RequirePermission>,
      },
      {
        path: 'integrations/sources/new',
        element: <RequirePermission permission="INTEGRATION_SOURCE_MANAGE"><IntegrationSourceCreatePage /></RequirePermission>,
      },
      {
        path: 'integrations/sources/:sourceId',
        element: <RequirePermission permission="INTEGRATION_SOURCE_READ"><IntegrationSourceDetailPage /></RequirePermission>,
      },
      {
        path: 'integrations/service-clients',
        element: <RequirePermission permission="SERVICE_CLIENT_READ"><ServiceClientsPage /></RequirePermission>,
      },
      {
        path: 'integrations/service-clients/new',
        element: <RequirePermission permission="SERVICE_CLIENT_CREATE"><ServiceClientCreatePage /></RequirePermission>,
      },
      {
        path: 'integrations/service-clients/:clientId',
        element: <RequirePermission permission="SERVICE_CLIENT_READ"><ServiceClientDetailPage /></RequirePermission>,
      },
      {
        path: 'imports',
        element: <RequirePermission permission="DOCUMENT_READ"><ImportsPage /></RequirePermission>,
      },
      {
        path: 'imports/new',
        element: <RequirePermission permission="DOCUMENT_GENERATE"><ImportCreatePage /></RequirePermission>,
      },
      {
        path: 'imports/:importId',
        element: <RequirePermission permission="DOCUMENT_READ"><ImportDetailPage /></RequirePermission>,
      },
      {
        path: 'imports/:importId/errors',
        element: <RequirePermission permission="DOCUMENT_READ"><ImportErrorsPage /></RequirePermission>,
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
