import { apiRequest } from '../../../shared/api/http-client';
import type {
  CreateServiceClientCommand,
  MappingTransformationDto,
  RotateServiceClientSecretCommand,
  CredentialIssuedDto,
  ServiceClientDto,
  ServiceClientScopeDto,
  IntegrationSourceDto, IntegrationSourceReadinessDto, CreateIntegrationSourceCommand, UpdateIntegrationSourceCommand,
  SourceSchemaDefinitionDto, MappingProfileDefinitionDto,
} from '../model/integration.types';

const enc = encodeURIComponent;

export const getServiceClients = () =>
  apiRequest<ServiceClientDto[]>('/api/v1/integration/service-clients');

export const getServiceClient = (id: string) =>
  apiRequest<ServiceClientDto>(`/api/v1/integration/service-clients/${enc(id)}`);

export const getServiceClientScopes = () =>
  apiRequest<ServiceClientScopeDto[]>('/api/v1/integration/service-client-scopes');

export const createServiceClient = (command: CreateServiceClientCommand) =>
  apiRequest<CredentialIssuedDto>('/api/v1/integration/service-clients', { method: 'POST', body: command });

export const rotateServiceClientSecret = (id: string, command: RotateServiceClientSecretCommand) =>
  apiRequest<CredentialIssuedDto>(
    `/api/v1/integration/service-clients/${enc(id)}/rotate-secret`,
    { method: 'POST', body: command },
  );

export const activateServiceClientCredential = (id: string, credentialId: string) =>
  apiRequest<void>(
    `/api/v1/integration/service-clients/${enc(id)}/credentials/${enc(credentialId)}/activate`,
    { method: 'POST' },
  );

export const blockServiceClient = (id: string) =>
  apiRequest<void>(`/api/v1/integration/service-clients/${enc(id)}/block`, { method: 'POST' });

export const unblockServiceClient = (id: string) =>
  apiRequest<void>(`/api/v1/integration/service-clients/${enc(id)}/unblock`, { method: 'POST' });

export const getMappingTransformations = () =>
  apiRequest<MappingTransformationDto[]>('/api/v1/mapping-metadata/transformations');

export const getIntegrationSources = () => apiRequest<IntegrationSourceDto[]>('/api/v1/integration/sources');
export const getIntegrationSource = (id: string) => apiRequest<IntegrationSourceDto>(`/api/v1/integration/sources/${enc(id)}`);
export const getIntegrationSourceReadiness = (id: string) => apiRequest<IntegrationSourceReadinessDto>(`/api/v1/integration/sources/${enc(id)}/readiness`);
export const createIntegrationSource = (command: CreateIntegrationSourceCommand) => apiRequest<IntegrationSourceDto>('/api/v1/integration/sources', { method: 'POST', body: command });
export const updateIntegrationSource = (id: string, command: UpdateIntegrationSourceCommand) => apiRequest<IntegrationSourceDto>(`/api/v1/integration/sources/${enc(id)}`, { method: 'PUT', body: command });
export const activateIntegrationSource = (id: string, version: number) => apiRequest<IntegrationSourceDto>(`/api/v1/integration/sources/${enc(id)}/activate`, { method: 'POST', body: { version } });
export const suspendIntegrationSource = (id: string, version: number) => apiRequest<IntegrationSourceDto>(`/api/v1/integration/sources/${enc(id)}/suspend`, { method: 'POST', body: { version } });
export const archiveIntegrationSource = (id: string, version: number) => apiRequest<IntegrationSourceDto>(`/api/v1/integration/sources/${enc(id)}/archive`, { method: 'POST', body: { version } });
export const getSourceSchemaDefinitions = () => apiRequest<SourceSchemaDefinitionDto[]>('/api/v1/source-schemas');
export const getMappingProfileDefinitions = () => apiRequest<MappingProfileDefinitionDto[]>('/api/v1/mapping-profiles');
