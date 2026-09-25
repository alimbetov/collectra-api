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

  it('uses backend-owned mapping metadata', async () => {
    await getMappingTransformations();
    expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-metadata/transformations');
  });
});
