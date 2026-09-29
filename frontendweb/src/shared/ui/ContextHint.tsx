import { useId, type ReactNode } from 'react';

interface ContextHintProps {
  children: ReactNode;
  label?: string;
}

export function ContextHint({ children, label = 'Подсказка' }: ContextHintProps) {
  const id = useId();
  return <p id={id} className="ui-context-hint" role="note" aria-label={label}>{children}</p>;
}
