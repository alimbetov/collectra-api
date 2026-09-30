import { describe, expect, it } from 'vitest';
import { navigation, canAccessNavigationItem } from './navigation';
import { personaHasAnyPermission, personaHasPermission, roleJourneyPersonas } from './role-journey.matrix';

const surfaceRules: Record<string, readonly string[]> = {
  '/customers': ['CUSTOMER_READ'],
  '/contracts': ['CONTRACT_READ'],
  '/receivables': ['RECEIVABLE_READ'],
  '/collections': ['COLLECTION_READ'],
  '/campaigns': ['CAMPAIGN_READ'],
  '/templates': ['TEMPLATE_READ'],
  '/integrations': ['INTEGRATION_SOURCE_READ', 'SERVICE_CLIENT_READ', 'SOURCE_SCHEMA_READ', 'MAPPING_PROFILE_READ'],
  '/imports': ['DOCUMENT_READ'],
  '/files': ['FILE_READ'],
  '/administration': ['USER_READ'],
  '/analytics': ['RECEIVABLE_READ', 'COLLECTION_READ', 'CAMPAIGN_READ'],
  '/operations': ['INTEGRATION_SOURCE_READ', 'CAMPAIGN_READ', 'DOCUMENT_READ'],
};

describe('role journey capability matrix', () => {
  it.each(roleJourneyPersonas)('$label allowed routes are backed by at least one required capability', (persona) => {
    for (const route of persona.allowedRoutes) {
      expect(surfaceRules[route], `missing surface rule for ${route}`).toBeDefined();
      expect(personaHasAnyPermission(persona, surfaceRules[route]!)).toBe(true);
    }
  });

  it.each(roleJourneyPersonas)('$label forbidden routes fail closed against their surface capabilities', (persona) => {
    for (const route of persona.forbiddenRoutes) {
      expect(surfaceRules[route], `missing surface rule for ${route}`).toBeDefined();
      expect(personaHasAnyPermission(persona, surfaceRules[route]!)).toBe(false);
    }
  });

  it.each(roleJourneyPersonas)('$label sidebar visibility agrees with persona permissions', (persona) => {
    for (const item of navigation) {
      const visible = canAccessNavigationItem(item, (permission) => personaHasPermission(persona, permission));
      if (item.path === '/') {
        expect(visible).toBe(true);
        continue;
      }
      const expected = persona.allowedRoutes.includes(item.path);
      expect(visible, `${persona.id} navigation mismatch at ${item.path}`).toBe(expected);
    }
  });

  it('keeps every permission-gated business surface represented by the persona contract', () => {
    expect(Object.keys(surfaceRules).sort()).toEqual([
      '/administration', '/analytics', '/campaigns', '/collections', '/contracts', '/customers',
      '/files', '/imports', '/integrations', '/operations', '/receivables', '/templates',
    ]);
  });
});
