import { matchRoutes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { router } from './router';

const primaryRoutes = [
  '/',
  '/customers',
  '/customers/11111111-1111-4111-8111-111111111111',
  '/customers/11111111-1111-4111-8111-111111111111/contacts',
  '/customers/11111111-1111-4111-8111-111111111111/contracts',
  '/customers/segments',\n  '/customers/segments/11111111-1111-4111-8111-111111111111',
  '/contracts',
  '/contracts/11111111-1111-4111-8111-111111111111',
  '/receivables',
  '/receivables/invoices/new',
  '/receivables/invoices/11111111-1111-4111-8111-111111111111',
  '/collections',
  '/collections/11111111-1111-4111-8111-111111111111',
  '/campaigns',
  '/campaigns/11111111-1111-4111-8111-111111111111',
  '/campaigns/11111111-1111-4111-8111-111111111111/runs/22222222-2222-4222-8222-222222222222',
  '/campaigns/11111111-1111-4111-8111-111111111111/runs/22222222-2222-4222-8222-222222222222/messages',
  '/campaigns/11111111-1111-4111-8111-111111111111/runs/22222222-2222-4222-8222-222222222222/messages/33333333-3333-4333-8333-333333333333',
  '/templates',
  '/templates/new',
  '/templates/assets',
  '/templates/11111111-1111-4111-8111-111111111111',
  '/templates/11111111-1111-4111-8111-111111111111/versions/22222222-2222-4222-8222-222222222222',
  '/templates/11111111-1111-4111-8111-111111111111/versions/22222222-2222-4222-8222-222222222222/builder',
  '/integrations',
  '/integrations/source-schemas',
  '/integrations/source-schemas/11111111-1111-4111-8111-111111111111',
  '/integrations/mapping-profiles',
  '/integrations/mapping-profiles/11111111-1111-4111-8111-111111111111',
  '/integrations/sources',
  '/integrations/sources/new',
  '/integrations/sources/11111111-1111-4111-8111-111111111111',
  '/integrations/service-clients',
  '/integrations/service-clients/new',
  '/integrations/service-clients/11111111-1111-4111-8111-111111111111',
  '/imports',
  '/imports/new',
  '/imports/11111111-1111-4111-8111-111111111111',
  '/imports/11111111-1111-4111-8111-111111111111/errors',
  '/files',
  '/files/new',
  '/files/11111111-1111-4111-8111-111111111111',
] as const;

const platformRoutes = [
  '/platform',
  '/platform/tenants',
  '/platform/tenants/11111111-1111-4111-8111-111111111111',
  '/platform/users',
  '/platform/users/11111111-1111-4111-8111-111111111111',
  '/platform/administrators',
] as const;

describe('primary route contract', () => {
  it.each([...primaryRoutes, ...platformRoutes])('matches a concrete product route for %s', (path) => {
    const matches = matchRoutes(router.routes, path);
    expect(matches).not.toBeNull();
    expect(matches?.at(-1)?.route.path).not.toBe('*');
  });

  it('keeps deferred VC9 and D04 platform surfaces out of the router', () => {
    for (const path of ['/platform/audit', '/platform/operations']) {
      const matches = matchRoutes(router.routes, path);
      expect(matches?.at(-1)?.route.path).toBe('*');
    }
  });
});
