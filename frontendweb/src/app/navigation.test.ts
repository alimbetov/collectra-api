import { describe, expect, it, vi } from 'vitest';
import { canAccessNavigationItem, navigation } from './navigation';

describe('workspace navigation permissions', () => {
  it('keeps human-only routes visible without inventing a frontend permission', () => {
    const hasPermission = vi.fn(() => false);
    const dashboard = navigation.find((item) => item.path === '/');

    expect(dashboard).toBeDefined();
    expect(canAccessNavigationItem(dashboard!, hasPermission)).toBe(true);
    expect(hasPermission).not.toHaveBeenCalled();
  });

  it('keeps the integration journey discoverable without inventing a backend permission', () => {
    const integrations = navigation.find((item) => item.path === '/integrations');

    expect(integrations).toBeDefined();
    expect(canAccessNavigationItem(integrations!, () => false)).toBe(true);
  });

  it('requires the exact backend permission for restricted domains', () => {
    const campaigns = navigation.find((item) => item.path === '/campaigns');

    expect(campaigns).toBeDefined();
    expect(canAccessNavigationItem(campaigns!, (code) => code === 'CAMPAIGN_READ')).toBe(true);
    expect(canAccessNavigationItem(campaigns!, () => false)).toBe(false);
  });
});
