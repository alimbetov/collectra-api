import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { HttpResponse, http } from 'msw';
import { setupServer } from 'msw/node';
import {
  customerDetailPath,
  customerEmailsPath,
  customerListPath,
  customerPhonesPath,
  changeCustomerStatus,
  getCustomer,
  getCustomerEmails,
  getCustomerPhones,
  getCustomers,
  getManagerOptions,
  getSegmentOptions,
  updateCustomer,
  addCustomerEmail,
  addCustomerPhone,
  updateCustomerEmail,
  updateCustomerPhone,
} from './customer.api';

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('customer API', () => {
  it('serializes only the fixed supported filters and safely encodes values', () => {
    expect(
      customerListPath({
        search: 'A&B + C',
        status: 'ACTIVE',
        page: 2,
        size: 25,
        sort: 'displayName,asc',
      }),
    ).toBe(
      '/api/v1/customers?search=A%26B+%2B+C&status=ACTIVE&page=2&size=25&sort=displayName%2Casc',
    );
  });

  it('builds tenant-derived detail and contact paths without query parameters', () => {
    const id = '11111111-1111-4111-8111-111111111111';
    expect(customerDetailPath(id)).toBe(`/api/v1/customers/${id}`);
    expect(customerEmailsPath(id)).toBe(`/api/v1/customers/${id}/emails`);
    expect(customerPhonesPath(id)).toBe(`/api/v1/customers/${id}/phones`);
    expect(customerDetailPath('unsafe/id')).toBe('/api/v1/customers/unsafe%2Fid');
  });

  it('loads enriched detail and both contact resources with three exact requests', async () => {
    const id = '11111111-1111-4111-8111-111111111111';
    const paths: string[] = [];
    server.use(
      http.get(`*/api/v1/customers/${id}`, ({ request }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json({ id, displayName: 'Acme', managerDisplayName: 'Manager', segments: [{ id: 's', code: 'VIP', name: 'VIP' }] });
      }),
      http.get(`*/api/v1/customers/${id}/emails`, ({ request }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json([{ id: 'e', email: 'a@b.kz', type: 'WORK', primary: true, verified: false, status: 'ACTIVE', version: 0 }]);
      }),
      http.get(`*/api/v1/customers/${id}/phones`, ({ request }) => {
        paths.push(new URL(request.url).pathname);
        return HttpResponse.json([{ id: 'p', phone: '+7701', normalizedPhone: '+7701', type: 'MOBILE', primary: true, verified: false, status: 'ACTIVE', version: 0 }]);
      }),
    );

    const [detail, emails, phones] = await Promise.all([
      getCustomer(id),
      getCustomerEmails(id),
      getCustomerPhones(id),
    ]);
    expect(detail).toMatchObject({ managerDisplayName: 'Manager', segments: [{ name: 'VIP' }] });
    expect(emails).toHaveLength(1);
    expect(phones).toHaveLength(1);
    expect(paths).toHaveLength(3);
    expect(paths.join('\n')).not.toContain('tenantId');
  });

  it('reads the enriched list projection with a single customer request', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/v1/customers', ({ request }) => {
        calls += 1;
        expect(new URL(request.url).searchParams.get('status')).toBe('ACTIVE');
        return HttpResponse.json({
          items: [{
            id: '11111111-1111-4111-8111-111111111111',
            externalId: 'EXT-1',
            customerType: 'COMPANY',
            displayName: 'Acme',
            status: 'ACTIVE',
            managerUserId: null,
            preferredLocale: null,
            timezone: null,
            segmentIds: [],
            primaryEmail: 'billing@acme.test',
            primaryPhone: '+77010000000',
            managerDisplayName: 'A. Manager',
            segments: [{ id: '22222222-2222-4222-8222-222222222222', code: 'vip', name: 'VIP' }],
            createdAt: '2026-09-17T10:00:00Z',
            updatedAt: '2026-09-18T10:00:00Z',
          }],
          page: 0,
          size: 50,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        });
      }),
    );

    const result = await getCustomers({ status: 'ACTIVE' });
    expect(result.items[0]).toMatchObject({ primaryEmail: 'billing@acme.test', managerDisplayName: 'A. Manager' });
    expect(calls).toBe(1);
  });

  it('keeps reference searches bounded and tenant-derived', async () => {
    const urls: string[] = [];
    server.use(
      http.get('*/api/v1/identity/user-options', ({ request }) => {
        urls.push(request.url);
        return HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
      }),
      http.get('*/api/v1/customer-segments', ({ request }) => {
        urls.push(request.url);
        return HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false });
      }),
    );

    await Promise.all([getManagerOptions('  Ivan  '), getSegmentOptions(' VIP ')]);
    expect(urls.join('\n')).not.toContain('tenantId');
    expect(urls[0]).toContain('status=ACTIVE&page=0&size=20&search=Ivan');
    expect(urls[1]).toContain('active=true&page=0&size=20&sort=name%2Casc&search=VIP');
  });

  it('sends profile and status commands with the captured version and no tenant id', async () => {
    const id = '11111111-1111-4111-8111-111111111111';
    const requests: Array<{ method: string; path: string; body: unknown }> = [];
    server.use(
      http.put(`*/api/v1/customers/${id}`, async ({ request }) => {
        requests.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() });
        return HttpResponse.json({ id, displayName: 'Updated', version: 8 });
      }),
      http.patch(`*/api/v1/customers/${id}/status`, async ({ request }) => {
        requests.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() });
        return HttpResponse.json({ id, displayName: 'Updated', status: 'BLOCKED', version: 9 });
      }),
    );

    await updateCustomer(id, {
      displayName: 'Updated', firstName: null, lastName: null, middleName: null,
      companyName: null, managerUserId: null, preferredLocale: null, timezone: null,
      customFields: null, version: 7,
    });
    await changeCustomerStatus(id, { status: 'BLOCKED', version: 8 });

    expect(requests).toEqual([
      { method: 'PUT', path: `/api/v1/customers/${id}`, body: expect.objectContaining({ displayName: 'Updated', version: 7 }) },
      { method: 'PATCH', path: `/api/v1/customers/${id}/status`, body: { status: 'BLOCKED', version: 8 } },
    ]);
    expect(JSON.stringify(requests)).not.toContain('tenantId');
  });

  it('sends create and versioned patch commands to encoded nested contact paths', async () => {
    const id = '11111111-1111-4111-8111-111111111111';
    const requests: Array<{ method: string; path: string; body: unknown }> = [];
    server.use(
      http.all('*/api/v1/customers/*', async ({ request }) => {
        requests.push({ method: request.method, path: new URL(request.url).pathname, body: await request.json() });
        return HttpResponse.json({ id: 'contact', version: 4 });
      }),
    );

    await addCustomerEmail(id, { email: 'a@b.kz', type: 'WORK', primary: true });
    await updateCustomerEmail(id, 'email/id', { type: 'HOME', primary: false, status: 'ACTIVE', version: 2 });
    await addCustomerPhone(id, { phone: '+7701', type: 'MOBILE', primary: false });
    await updateCustomerPhone(id, 'phone/id', { type: 'WORK', primary: false, status: 'INACTIVE', version: 3 });

    expect(requests.map(({ method, path }) => `${method} ${path}`)).toEqual([
      `POST /api/v1/customers/${id}/emails`,
      `PATCH /api/v1/customers/${id}/emails/email%2Fid`,
      `POST /api/v1/customers/${id}/phones`,
      `PATCH /api/v1/customers/${id}/phones/phone%2Fid`,
    ]);
    expect(requests[1].body).toEqual(expect.objectContaining({ version: 2 }));
    expect(requests[3].body).toEqual(expect.objectContaining({ version: 3 }));
    expect(JSON.stringify(requests)).not.toContain('tenantId');
  });
});
