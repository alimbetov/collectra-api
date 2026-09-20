import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { HttpResponse, http } from 'msw';
import { setupServer } from 'msw/node';
import {
  contractDetailPath, contractListPath, createContract, getContract, getContracts,
  transitionContract, updateContract,
} from './contract.api';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('contract API', () => {
  it('serializes only supported fixed filters', () => {
    expect(contractListPath({ search: 'A&B', status: 'ACTIVE', page: 2, size: 20, sort: 'contractNumber,asc' }))
      .toBe('/api/v1/contracts?search=A%26B&status=ACTIVE&page=2&size=20&sort=contractNumber%2Casc');
    expect(contractDetailPath('unsafe/id')).toBe('/api/v1/contracts/unsafe%2Fid');
  });

  it('sends create, versioned update and lifecycle commands without tenant id', async () => {
    const id = '11111111-1111-4111-8111-111111111111';
    const requests: Array<{ method: string; path: string; body?: unknown }> = [];
    server.use(
      http.get('*/api/v1/contracts', ({ request }) => { requests.push({ method: request.method, path: new URL(request.url).pathname }); return HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false }); }),
      http.get('*/api/v1/contracts/*', ({ request }) => { requests.push({ method: request.method, path: new URL(request.url).pathname }); return HttpResponse.json({ id, version: 3 }); }),
      http.post('*/api/v1/contracts', async ({ request }) => { requests.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() }); return HttpResponse.json({ id, version: 0 }, { status: 201 }); }),
      http.put('*/api/v1/contracts/*', async ({ request }) => { requests.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() }); return HttpResponse.json({ id, version: 4 }); }),
      http.post('*/api/v1/contracts/*/suspend', async ({ request }) => { requests.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() }); return HttpResponse.json({ id, version: 4 }); }),
    );
    await getContracts({ page: 0, size: 20 });
    await getContract(id);
    await createContract({ customerId: 'customer', externalId: 'EXT', contractNumber: 'CN', validFrom: '2026-01-01', validTo: null, renewalDate: null, customFields: null });
    await updateContract(id, { contractNumber: 'CN-2', validFrom: '2026-01-01', validTo: null, renewalDate: null, customFields: null, version: 3 });
    await transitionContract(id, 'suspend', 3);

    expect(requests[3].body).toEqual(expect.objectContaining({ version: 3 }));
    expect(requests[4].body).toEqual({ version: 3 });
    expect(JSON.stringify(requests)).not.toContain('tenantId');
  });
});
