import { afterEach, describe, expect, it, vi } from 'vitest';
import { bffRequest } from './bff-client';

afterEach(() => vi.unstubAllGlobals());

describe('IAM-R3.2B tokenless BFF transport', () => {
  it('rejects path escape before network access', async () => {
    const fetcher = vi.fn();
    vi.stubGlobal('fetch', fetcher);
    await expect(bffRequest('/api/v1/../admin')).rejects.toThrow('Invalid');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('uses same-origin cookies and never forwards caller bearer token', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      ok: true, status: 200, json: async () => ({ ok: true }),
    });
    vi.stubGlobal('fetch', fetcher);
    await bffRequest('/api/v1/identity/me', {
      headers: { Authorization: 'Bearer forbidden', Accept: 'application/json' },
    });
    expect(fetcher).toHaveBeenCalledTimes(1);
    const [url, init] = fetcher.mock.calls[0];
    expect(url).toBe('/api/v1/iam/collectra/api/v1/identity/me');
    expect(init.credentials).toBe('same-origin');
    expect(init.headers.has('Authorization')).toBe(false);
  });

  it('obtains CSRF token before POST', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () =>
        ({ headerName: 'X-XSRF-TOKEN', token: 'csrf-value' }) })
      .mockResolvedValueOnce({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetcher);
    await bffRequest('/api/v1/identity/action', { method: 'POST' });
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[1][1].headers.get('X-XSRF-TOKEN')).toBe('csrf-value');
  });
});
