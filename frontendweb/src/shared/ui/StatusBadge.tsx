import type { PropsWithChildren } from 'react';

export function StatusBadge({ children, tone = 'neutral' }: PropsWithChildren<{ tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger' }>) {
  return <span className={`ui-status-badge ui-status-badge--${tone}`}>{children}</span>;
}
