import type { ReactNode } from 'react';

interface ActionGuardProps {
  available: boolean;
  reason?: ReactNode;
  children: ReactNode;
}

export function ActionGuard({ available, reason, children }: ActionGuardProps) {
  return (
    <div className="ui-action-guard">
      {children}
      {!available && reason ? <p className="ui-action-guard__reason" role="note">{reason}</p> : null}
    </div>
  );
}
