import type { PropsWithChildren, ReactNode } from 'react';

interface AlertProps {
  variant?: 'info' | 'success' | 'warning' | 'danger';
  title?: ReactNode;
}

export function Alert({ children, title, variant = 'info' }: PropsWithChildren<AlertProps>) {
  return (
    <div className={`ui-alert ui-alert--${variant}`} role={variant === 'danger' ? 'alert' : 'status'}>
      {title ? <strong>{title}</strong> : null}
      <div>{children}</div>
    </div>
  );
}
