export interface ServiceClientDto {
  id: string;
  clientId: string;
  name: string;
  scopes: string[];
  status: string;
  expiresAt: string | null;
  credentialId: string | null;
  secretHint: string | null;
  secretExpiresAt: string | null;
  credentialStatus: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface ServiceClientScopeDto { code: string }
export interface CreateServiceClientCommand {
  clientId: string; name: string; clientSecret: string; scopes: string[];
  expiresAt?: string | null; secretExpiresAt?: string | null;
}
export interface RotateServiceClientSecretCommand {
  clientSecret: string; secretExpiresAt?: string | null;
}
export interface RotateServiceClientSecretResponse {
  credentialId: string; secretHint: string; secretExpiresAt: string | null;
}
export interface MappingTransformationDto {
  code: string;
  recommendedTargetTypes: string[];
  parameterSchema: Record<string, unknown>;
}
