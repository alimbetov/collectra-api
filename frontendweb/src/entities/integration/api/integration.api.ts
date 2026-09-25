import { apiRequest } from '../../../shared/api/http-client';
import type {
  CreateServiceClientCommand,
  MappingTransformationDto,
  RotateServiceClientSecretCommand,
  CredentialIssuedDto,
  ServiceClientDto,
  ServiceClientScopeDto,
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
