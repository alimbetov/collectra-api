import { useEffect, useId, useRef, type PropsWithChildren, type ReactNode } from 'react';

interface DialogProps {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  actions?: ReactNode;
  closeLabel?: string;
}

export function Dialog({ open, title, onClose, actions, closeLabel = 'Закрыть', children }: PropsWithChildren<DialogProps>) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    panelRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="ui-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div ref={panelRef} className="ui-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <header><h2 id={titleId}>{title}</h2><button type="button" className="ui-dialog__close" aria-label={closeLabel} onClick={onClose}>×</button></header>
        <div className="ui-dialog__body">{children}</div>
        {actions ? <footer>{actions}</footer> : null}
      </div>
    </div>
  );
}
