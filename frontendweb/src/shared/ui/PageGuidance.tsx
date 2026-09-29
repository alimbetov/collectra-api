import type { ReactNode } from 'react';

interface PageGuidanceProps {
  title: ReactNode;
  description: ReactNode;
  nextAction?: ReactNode;
}

export function PageGuidance({ title, description, nextAction }: PageGuidanceProps) {
  return (
    <aside className="ui-page-guidance" aria-label="Помощь по текущему процессу">
      <strong>{title}</strong>
      <p>{description}</p>
      {nextAction ? <p><strong>Следующий шаг:</strong> {nextAction}</p> : null}
    </aside>
  );
}
