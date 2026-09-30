export interface CommandCapability {
  id: string;
  permission: string;
  backendEvidence: string;
}

export const commandCapabilities: readonly CommandCapability[] = [
  { id: 'payment.create', permission: 'RECEIVABLE_MANAGE', backendEvidence: 'ReceivableController#createPayment' },
  { id: 'payment.allocate', permission: 'RECEIVABLE_MANAGE', backendEvidence: 'ReceivableController#allocatePayment' },
  { id: 'payment.reverseAllocation', permission: 'RECEIVABLE_MANAGE', backendEvidence: 'ReceivableController#reverseAllocation' },
  { id: 'collection.manageCase', permission: 'COLLECTION_MANAGE', backendEvidence: 'CollectionController mutation endpoints' },
  { id: 'collection.managePromise', permission: 'COLLECTION_MANAGE', backendEvidence: 'CollectionController promise endpoints' },
  { id: 'collection.manageDispute', permission: 'COLLECTION_MANAGE', backendEvidence: 'CollectionController dispute endpoints' },
  { id: 'collection.manageAction', permission: 'COLLECTION_MANAGE', backendEvidence: 'CollectionController action endpoints' },
  { id: 'campaign.manage', permission: 'CAMPAIGN_MANAGE', backendEvidence: 'CampaignController mutation endpoints' },
  { id: 'template.manage', permission: 'TEMPLATE_MANAGE', backendEvidence: 'TemplateManagementController mutation endpoints' },
  { id: 'template.publish', permission: 'TEMPLATE_PUBLISH', backendEvidence: 'TemplateManagementController publish/reopen/archive' },
  { id: 'integration.manageSource', permission: 'INTEGRATION_SOURCE_MANAGE', backendEvidence: 'IntegrationSourceController mutation endpoints' },
  { id: 'integration.createServiceClient', permission: 'SERVICE_CLIENT_CREATE', backendEvidence: 'ServiceClientController#create' },
  { id: 'integration.rotateServiceSecret', permission: 'SERVICE_CLIENT_ROTATE_SECRET', backendEvidence: 'ServiceClientController#rotateSecret' },
  { id: 'integration.blockServiceClient', permission: 'SERVICE_CLIENT_BLOCK', backendEvidence: 'ServiceClientController block/unblock' },
  { id: 'rbac.createRole', permission: 'ROLE_CREATE', backendEvidence: 'TenantRoleController#create' },
  { id: 'rbac.updateRole', permission: 'ROLE_UPDATE', backendEvidence: 'TenantRoleController update/delete' },
  { id: 'rbac.assignRole', permission: 'ROLE_ASSIGN', backendEvidence: 'TenantMembershipController#replaceRoles' },
  { id: 'rbac.blockUser', permission: 'USER_BLOCK', backendEvidence: 'TenantMembershipController#changeStatus' },
  { id: 'rbac.revokeSessions', permission: 'USER_UPDATE', backendEvidence: 'TenantMembershipController session revocation' },
] as const;

export function canExecuteCommand(permissions: readonly string[], commandId: string): boolean {
  const command = commandCapabilities.find((candidate) => candidate.id === commandId);
  if (!command) throw new Error(`Unknown role-journey command: ${commandId}`);
  return permissions.includes(command.permission);
}
