export interface RoleJourneyPersona {
  id: string;
  label: string;
  permissions: readonly string[];
  allowedRoutes: readonly string[];
  forbiddenRoutes: readonly string[];
}

export const roleJourneyPersonas: readonly RoleJourneyPersona[] = [
  {
    id: 'tenant-admin',
    label: 'Tenant Administrator',
    permissions: [
      'CUSTOMER_READ', 'CUSTOMER_MANAGE', 'CONTRACT_READ', 'CONTRACT_MANAGE',
      'RECEIVABLE_READ', 'RECEIVABLE_MANAGE', 'COLLECTION_READ', 'COLLECTION_MANAGE',
      'CAMPAIGN_READ', 'CAMPAIGN_MANAGE', 'TEMPLATE_READ', 'TEMPLATE_MANAGE',
      'INTEGRATION_SOURCE_READ', 'INTEGRATION_SOURCE_MANAGE', 'SERVICE_CLIENT_READ',
      'SOURCE_SCHEMA_READ', 'MAPPING_PROFILE_READ', 'DOCUMENT_READ', 'DOCUMENT_GENERATE',
      'FILE_READ', 'FILE_UPLOAD', 'USER_READ', 'ROLE_CREATE', 'ROLE_UPDATE',
    ],
    allowedRoutes: ['/customers', '/contracts', '/receivables', '/collections', '/campaigns', '/templates', '/integrations', '/imports', '/files', '/administration', '/analytics', '/operations'],
    forbiddenRoutes: [],
  },
  {
    id: 'receivables-manager',
    label: 'Receivables Manager',
    permissions: ['CUSTOMER_READ', 'CONTRACT_READ', 'RECEIVABLE_READ', 'RECEIVABLE_MANAGE', 'COLLECTION_READ'],
    allowedRoutes: ['/customers', '/contracts', '/receivables', '/collections', '/analytics'],
    forbiddenRoutes: ['/campaigns', '/templates', '/integrations', '/administration'],
  },
  {
    id: 'collection-operator',
    label: 'Collection Operator',
    permissions: ['CUSTOMER_READ', 'RECEIVABLE_READ', 'COLLECTION_READ', 'COLLECTION_MANAGE'],
    allowedRoutes: ['/customers', '/receivables', '/collections', '/analytics'],
    forbiddenRoutes: ['/integrations', '/administration'],
  },
  {
    id: 'campaign-manager',
    label: 'Campaign Manager',
    permissions: ['CAMPAIGN_READ', 'CAMPAIGN_MANAGE', 'TEMPLATE_READ'],
    allowedRoutes: ['/campaigns', '/templates', '/analytics', '/operations'],
    forbiddenRoutes: ['/receivables', '/integrations', '/administration'],
  },
  {
    id: 'template-manager',
    label: 'Template Manager',
    permissions: ['TEMPLATE_READ', 'TEMPLATE_MANAGE', 'FILE_READ'],
    allowedRoutes: ['/templates', '/files'],
    forbiddenRoutes: ['/campaigns', '/integrations', '/administration', '/analytics', '/operations'],
  },
  {
    id: 'integration-admin',
    label: 'Integration Administrator',
    permissions: ['INTEGRATION_SOURCE_READ', 'INTEGRATION_SOURCE_MANAGE', 'SERVICE_CLIENT_READ', 'SERVICE_CLIENT_CREATE', 'SOURCE_SCHEMA_READ', 'MAPPING_PROFILE_READ', 'DOCUMENT_READ'],
    allowedRoutes: ['/integrations', '/imports', '/operations'],
    forbiddenRoutes: ['/campaigns', '/administration', '/analytics'],
  },
  {
    id: 'auditor',
    label: 'Read-only Auditor',
    permissions: ['CUSTOMER_READ', 'CONTRACT_READ', 'RECEIVABLE_READ', 'COLLECTION_READ', 'CAMPAIGN_READ', 'TEMPLATE_READ', 'DOCUMENT_READ', 'FILE_READ'],
    allowedRoutes: ['/customers', '/contracts', '/receivables', '/collections', '/campaigns', '/templates', '/imports', '/files', '/analytics', '/operations'],
    forbiddenRoutes: ['/administration'],
  },
  {
    id: 'minimal-user',
    label: 'Minimal Tenant User',
    permissions: ['USER_READ'],
    allowedRoutes: ['/administration'],
    forbiddenRoutes: ['/customers', '/receivables', '/collections', '/campaigns', '/templates', '/integrations', '/analytics', '/operations'],
  },
] as const;

export function personaHasPermission(persona: RoleJourneyPersona, permission: string): boolean {
  return persona.permissions.includes(permission);
}

export function personaHasAnyPermission(persona: RoleJourneyPersona, permissions: readonly string[]): boolean {
  return permissions.some((permission) => personaHasPermission(persona, permission));
}
