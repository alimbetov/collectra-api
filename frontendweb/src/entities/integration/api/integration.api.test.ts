import { describe, expect, it, vi, beforeEach } from 'vitest';
import * as http from '../../../shared/api/http-client';
import {
  activateServiceClientCredential,
  blockServiceClient,
  createServiceClient,
  getMappingTransformations,
  getServiceClient,
  getServiceClients,
  getServiceClientScopes,
  rotateServiceClientSecret,
  unblockServiceClient,
  getIntegrationSources, getIntegrationSource, getIntegrationSourceReadiness, createIntegrationSource,
  activateIntegrationSource, suspendIntegrationSource, archiveIntegrationSource, getSourceSchemaDefinitions, getMappingProfileDefinitions,
} from './integration.api';

vi.mock('../../../shared/api/http-client', () => ({ apiRequest: vi.fn() }));
const request = vi.mocked(http.apiRequest);

describe('integration api contracts', () => {
  beforeEach(() => request.mockReset());

  it('uses exact service client read contracts', async () => {
    await getServiceClients();
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients');
    await getServiceClient('a/b');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/a%2Fb');
    await getServiceClientScopes();
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-client-scopes');
  });

  it('uses exact service client mutation contracts', async () => {
    const command = { clientId: 'erp', name: 'ERP', scopes: ['integration:imports:read'] };
    await createServiceClient(command);
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients', { method: 'POST', body: command });

    const rotate = {};
    await rotateServiceClientSecret('client', rotate);
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/rotate-secret', { method: 'POST', body: rotate });

    await activateServiceClientCredential('client', 'credential');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/credentials/credential/activate', { method: 'POST' });

    await blockServiceClient('client');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/block', { method: 'POST' });
    await unblockServiceClient('client');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/unblock', { method: 'POST' });
  });

  it('never sends or expects a client secret on safe read contracts', async () => {
    await getServiceClients();
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients');
    await getServiceClient('client');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client');
  });

  it('uses exact integration source contracts', async () => {
    await getIntegrationSources(); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources');
    await getIntegrationSource('a/b'); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/a%2Fb');
    await getIntegrationSourceReadiness('source'); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/readiness');
    const command={code:'erp',name:'ERP',serviceClientId:'c',sourceSchemaDefinitionId:'s',mappingProfileDefinitionId:'m'};
    await createIntegrationSource(command); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources',{method:'POST',body:command});
    await activateIntegrationSource('source',3); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/activate',{method:'POST',body:{version:3}});
    await suspendIntegrationSource('source',4); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/suspend',{method:'POST',body:{version:4}});
    await archiveIntegrationSource('source',5); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/archive',{method:'POST',body:{version:5}});
    await getSourceSchemaDefinitions(); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas');
    await getMappingProfileDefinitions(); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles');
  });

  it('uses backend-owned mapping metadata', async () => {
    await getMappingTransformations();
    expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-metadata/transformations');
  });
});
