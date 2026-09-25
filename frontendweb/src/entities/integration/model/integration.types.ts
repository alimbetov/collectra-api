export interface ServiceClientDto {
  id: string;
  clientId: string;
  name: string;
  scopes: string[];
  status: string;
  expiresAt: string | null;
  credentialId: string | null;
  secretHint: string | null;
  credentialStatus: string | null;
  secretExpiresAt: string | null;
  lastUsedAt: string | null;
}
export interface ServiceClientScopeDto { code: string }
export interface CreateServiceClientCommand {
  clientId: string; name: string; scopes: string[];
  expiresAt?: string | null; secretExpiresAt?: string | null;
}
export interface RotateServiceClientSecretCommand { secretExpiresAt?: string | null }
export interface CredentialIssuedDto { client: ServiceClientDto; clientSecret: string }
export interface MappingTransformationDto {
  code: string;
  recommendedTargetTypes: string[];
  parameterSchema: Record<string, unknown>;
}
