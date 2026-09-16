import type { ButtonHTMLAttributes, PropsWithChildren } from 'react';
import { Spinner } from './Spinner';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger';
  loading?: boolean;
}

export function Button({ children, className = '', variant = 'primary', loading = false, disabled, ...props }: PropsWithChildren<ButtonProps>) {
  return (
    <button
      className={`ui-button ui-button--${variant} ${className}`.trim()}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <Spinner size="small" label="" /> : null}
      <span>{children}</span>
    </button>
  );
}
