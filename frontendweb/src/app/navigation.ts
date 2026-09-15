export interface NavigationItem {
  label: string;
  path: string;
  permission?: string;
}

export const navigation: readonly NavigationItem[] = [
  { label: 'Dashboard', path: '/' },
  { label: 'Customers', path: '/customers' },
  { label: 'Receivables', path: '/receivables' },
  { label: 'Collections', path: '/collections' },
  { label: 'Campaigns', path: '/campaigns', permission: 'CAMPAIGN_READ' },
  { label: 'Templates', path: '/templates', permission: 'TEMPLATE_READ' },
  { label: 'Imports', path: '/imports', permission: 'DOCUMENT_READ' },
  { label: 'Files', path: '/files', permission: 'FILE_READ' },
] as const;

export function canAccessNavigationItem(
  item: NavigationItem,
  hasPermission: (permission: string) => boolean,
): boolean {
  return item.permission === undefined || hasPermission(item.permission);
}
