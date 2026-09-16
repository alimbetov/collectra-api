interface SpinnerProps {
  label?: string;
  size?: 'small' | 'medium';
}

export function Spinner({ label = 'Загрузка', size = 'medium' }: SpinnerProps) {
  return (
    <span className={`ui-spinner ui-spinner--${size}`} role={label ? 'status' : undefined} aria-label={label || undefined}>
      <span className="ui-spinner__circle" aria-hidden="true" />
    </span>
  );
}
