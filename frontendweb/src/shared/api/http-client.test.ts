import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { HttpResponse, http } from 'msw';
import { setupServer } from 'msw/node';
import { ApiError, apiBlobRequest, apiRequest } from './http-client';

const server = setupServer();

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  server.resetHandlers();
});

afterAll(() => {
  server.close();
});

describe('apiRequest', () => {
  it('returns parsed JSON for a successful response', async () => {
    server.use(
      http.get('http://localhost/api/test', () =>
        HttpResponse.json({ status: 'ok' }),
      ),
    );

    await expect(apiRequest<{ status: string }>('http://localhost/api/test')).resolves.toEqual({
      status: 'ok',
    });
  });

  it('maps RFC7807 responses to ApiError', async () => {
    server.use(
      http.get('http://localhost/api/problem', () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Forbidden',
            status: 403,
            detail: 'Permission denied',
            code: 'FORBIDDEN',
            traceId: 'trace-1',
          },
          { status: 403 },
        ),
      ),
    );

    try {
      await apiRequest('http://localhost/api/problem');
      throw new Error('Expected apiRequest to fail');
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      const apiError = error as ApiError;
      expect(apiError.status).toBe(403);
      expect(apiError.problem?.code).toBe('FORBIDDEN');
      expect(apiError.problem?.traceId).toBe('trace-1');
    }
  });

  it('returns undefined for 204 responses', async () => {
    server.use(
      http.delete('http://localhost/api/test', () => new HttpResponse(null, { status: 204 })),
    );

    await expect(
      apiRequest<void>('http://localhost/api/test', { method: 'DELETE' }),
    ).resolves.toBeUndefined();
  });
});


describe('apiBlobRequest', () => {
  it('preserves explicit Accept header and returns binary content', async () => {
    server.use(
      http.post('http://localhost/api/pdf', async ({ request }) => {
        expect(request.headers.get('accept')).toBe('application/pdf');
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), {
          status: 200,
          headers: { 'Content-Type': 'application/pdf' },
        });
      }),
    );

    const blob = await apiBlobRequest('http://localhost/api/pdf', {
      method: 'POST',
      headers: { Accept: 'application/pdf' },
      body: { draft: {}, payload: {} },
    });

    expect(blob.type).toBe('application/pdf');
    expect(blob.size).toBe(4);
  });
});
