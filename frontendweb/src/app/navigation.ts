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
  anyPermission?: readonly string[];
}

export const navigation: readonly NavigationItem[] = [
  { labelKey: 'navigation.dashboard', path: '/' },
  { labelKey: 'navigation.customers', path: '/customers', permission: 'CUSTOMER_READ' },
  { labelKey: 'navigation.contracts', path: '/contracts', permission: 'CONTRACT_READ' },
  { labelKey: 'navigation.receivables', path: '/receivables', permission: 'RECEIVABLE_READ' },
  { labelKey: 'navigation.collections', path: '/collections', permission: 'COLLECTION_READ' },
  { labelKey: 'navigation.campaigns', path: '/campaigns', permission: 'CAMPAIGN_READ' },
  { labelKey: 'navigation.templates', path: '/templates', permission: 'TEMPLATE_READ' },
  {
    labelKey: 'navigation.integrations',
    path: '/integrations',
    anyPermission: [
      'INTEGRATION_SOURCE_READ',
      'SERVICE_CLIENT_READ',
      'SOURCE_SCHEMA_READ',
      'MAPPING_PROFILE_READ',
    ],
  },
  { labelKey: 'navigation.imports', path: '/imports', permission: 'DOCUMENT_READ' },
  { labelKey: 'navigation.files', path: '/files', permission: 'FILE_READ' },
] as const;

export function canAccessNavigationItem(
  item: NavigationItem,
  hasPermission: (permission: string) => boolean,
): boolean {
  if (item.permission !== undefined) return hasPermission(item.permission);
  if (item.anyPermission !== undefined) return item.anyPermission.some(hasPermission);
  return true;
}
