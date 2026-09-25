export interface NavigationItem {
  labelKey:
    | 'navigation.dashboard'
    | 'navigation.customers'
    | 'navigation.contracts'
    | 'navigation.receivables'
    | 'navigation.collections'
    | 'navigation.campaigns'
    | 'navigation.templates'
    | 'navigation.imports'
    | 'navigation.files'
    | 'navigation.integrations';
  path: string;
  permission?: string;
}

export const navigation: readonly NavigationItem[] = [
  { labelKey: 'navigation.dashboard', path: '/' },
  { labelKey: 'navigation.customers', path: '/customers' },
  { labelKey: 'navigation.contracts', path: '/contracts' },
  { labelKey: 'navigation.receivables', path: '/receivables' },
  { labelKey: 'navigation.collections', path: '/collections' },
  { labelKey: 'navigation.campaigns', path: '/campaigns', permission: 'CAMPAIGN_READ' },
  { labelKey: 'navigation.templates', path: '/templates', permission: 'TEMPLATE_READ' },
  { labelKey: 'navigation.imports', path: '/imports', permission: 'DOCUMENT_READ' },
  { labelKey: 'navigation.files', path: '/files', permission: 'FILE_READ' },
  { labelKey: 'navigation.integrations', path: '/integrations' },
] as const;

export function canAccessNavigationItem(
  item: NavigationItem,
  hasPermission: (permission: string) => boolean,
): boolean {
  return item.permission === undefined || hasPermission(item.permission);
}
