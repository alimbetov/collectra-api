import { describe, expect, it, vi } from 'vitest';
import { canAccessNavigationItem, navigation } from './navigation';

describe('workspace navigation permissions', () => {
  it('keeps only the dashboard as the human-authenticated unscoped workspace entry', () => {
    const hasPermission = vi.fn(() => false);
    const dashboard = navigation.find((item) => item.path === '/');
    expect(dashboard).toBeDefined();
    expect(canAccessNavigationItem(dashboard!, hasPermission)).toBe(true);
    expect(hasPermission).not.toHaveBeenCalled();
  });

  it.each([
    ['/customers', 'CUSTOMER_READ'],
    ['/contracts', 'CONTRACT_READ'],
    ['/receivables', 'RECEIVABLE_READ'],
    ['/collections', 'COLLECTION_READ'],
  ])('guards %s with %s', (path, permission) => {
    const item = navigation.find((candidate) => candidate.path === path);
    expect(item).toBeDefined();
    expect(canAccessNavigationItem(item!, (code) => code === permission)).toBe(true);
    expect(canAccessNavigationItem(item!, () => false)).toBe(false);
  });

  it('requires the exact backend permission for restricted domains', () => {
    const campaigns = navigation.find((item) => item.path === '/campaigns');

    expect(campaigns).toBeDefined();
    expect(canAccessNavigationItem(campaigns!, (code) => code === 'CAMPAIGN_READ')).toBe(true);
    expect(canAccessNavigationItem(campaigns!, () => false)).toBe(false);
  });

  it.each([
    'INTEGRATION_SOURCE_READ',
    'SERVICE_CLIENT_READ',
    'SOURCE_SCHEMA_READ',
    'MAPPING_PROFILE_READ',
  ])('shows Integrations navigation for %s', (permission) => {
    const integrations = navigation.find((item) => item.path === '/integrations');
    expect(integrations).toBeDefined();
    expect(canAccessNavigationItem(integrations!, (code) => code === permission)).toBe(true);
  });

  it('hides Integrations navigation without any integration read capability', () => {
    const integrations = navigation.find((item) => item.path === '/integrations');
    expect(integrations).toBeDefined();
    expect(canAccessNavigationItem(integrations!, () => false)).toBe(false);
  });
});
