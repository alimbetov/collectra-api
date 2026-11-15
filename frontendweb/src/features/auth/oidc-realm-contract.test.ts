import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

interface RealmClient {
  clientId: string;
  publicClient: boolean;
  standardFlowEnabled: boolean;
  directAccessGrantsEnabled: boolean;
  implicitFlowEnabled?: boolean;
  redirectUris?: string[];
  webOrigins?: string[];
  attributes?: Record<string, string>;
  protocolMappers?: Array<{ config: Record<string, string> }>;
}

const realm = JSON.parse(
  readFileSync(new URL('../../../../app-common/infra/realm-shared.json', import.meta.url), 'utf8'),
) as { clients: RealmClient[] };

describe('IAM-R3.2 browser OIDC realm contract', () => {
  const client = realm.clients.find((item) => item.clientId === 'collectra-web');

  it('requires authorization code + S256 PKCE and disables password grant', () => {
    expect(client).toBeDefined();
    expect(client?.publicClient).toBe(true);
    expect(client?.standardFlowEnabled).toBe(true);
    expect(client?.directAccessGrantsEnabled).toBe(false);
    expect(client?.implicitFlowEnabled).toBe(false);
    expect(client?.attributes?.['pkce.code.challenge.method']).toBe('S256');
  });

  it('allows only the explicit development callback and origin', () => {
    expect(client?.redirectUris).toEqual(['http://localhost:8090/api/v1/iam/callback']);
    expect(client?.webOrigins).toEqual(['http://localhost:5173']);
  });

  it('keeps machine clients distinct from the browser client', () => {
    for (const id of ['collectra-test', 'app-b-test']) {
      const machine = realm.clients.find((item) => item.clientId === id);
      expect(machine?.publicClient).toBe(false);
      expect(machine?.standardFlowEnabled).toBe(false);
    }
  });

  it('issues a Collectra-specific API audience', () => {
    expect(client?.protocolMappers?.some(
      (mapper) => mapper.config['included.custom.audience'] === 'collectra-api'
        && mapper.config['access.token.claim'] === 'true',
    )).toBe(true);
  });
});
