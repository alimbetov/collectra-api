export interface ServiceClientDto {
  id: string; clientId: string; name: string; scopes: string[]; status: string;
  expiresAt: string | null; credentialId: string | null; secretHint: string | null;
  credentialStatus: string | null; secretExpiresAt: string | null; lastUsedAt: string | null;
}
export interface ServiceClientScopeDto { code: string }
export interface CreateServiceClientCommand { clientId: string; name: string; scopes: string[]; expiresAt?: string | null; secretExpiresAt?: string | null }
export interface RotateServiceClientSecretCommand { secretExpiresAt?: string | null }
export interface CredentialIssuedDto { client: ServiceClientDto; clientSecret: string }
export interface MappingTransformationDto { code: string; recommendedTargetTypes: string[]; parameterSchema: Record<string, unknown> }

export type IntegrationSourceStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export interface IntegrationSourceDto {
  id: string; code: string; name: string; status: IntegrationSourceStatus;
  serviceClientId: string; sourceSchemaDefinitionId: string; mappingProfileDefinitionId: string;
  processingMode: string; headerMapping: Record<string, unknown>; resourcePolicy: Record<string, unknown>;
  routingConfig: Record<string, unknown>; version: number;
}
export interface IntegrationReadinessCheckDto { code: string; state: 'READY' | 'BLOCKED'; resource: string }
export interface IntegrationSourceReadinessDto {
  integrationSourceId: string; sourceCode: string; status: IntegrationSourceStatus;
  ready: boolean; checks: IntegrationReadinessCheckDto[];
}
export interface CreateIntegrationSourceCommand {
  code: string; name: string; serviceClientId: string; sourceSchemaDefinitionId: string;
  mappingProfileDefinitionId: string; processingMode?: string;
  headerMapping?: Record<string, unknown>; resourcePolicy?: Record<string, unknown>; routingConfig?: Record<string, unknown>;
}
export interface UpdateIntegrationSourceCommand extends Omit<CreateIntegrationSourceCommand, 'code'> { version: number }
export interface SourceSchemaDefinitionDto { id: string; code: string; name: string }
export interface MappingProfileDefinitionDto { id: string; code: string; name: string; documentType: string }
