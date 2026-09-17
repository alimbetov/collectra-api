import { useEffect, useId, useRef, type PropsWithChildren, type ReactNode } from 'react';

const focusableSelector = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

interface DialogProps {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  actions?: ReactNode;
  closeLabel?: string;
  closeDisabled?: boolean;
}

export function Dialog({ open, title, onClose, actions, closeLabel = 'Закрыть', closeDisabled = false, children }: PropsWithChildren<DialogProps>) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const panel = panelRef.current;
    const focusable = () =>
      Array.from(panel?.querySelectorAll<HTMLElement>(focusableSelector) ?? []).filter(
        (element) => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true',
      );
    (focusable()[0] ?? panel)?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        if (!closeDisabled) onClose();
        return;
      }
      if (event.key !== 'Tab') return;
      const elements = focusable();
      if (elements.length === 0) {
        event.preventDefault();
        panel?.focus();
        return;
      }
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, [closeDisabled, open, onClose]);

  if (!open) return null;
  return (
    <div className="ui-dialog-backdrop" onMouseDown={(event) => { if (!closeDisabled && event.target === event.currentTarget) onClose(); }}>
      <div ref={panelRef} className="ui-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
        <header><h2 id={titleId}>{title}</h2><button type="button" className="ui-dialog__close" aria-label={closeLabel} disabled={closeDisabled} onClick={onClose}>×</button></header>
        <div className="ui-dialog__body">{children}</div>
        {actions ? <footer>{actions}</footer> : null}
      </div>
    </div>
  );
}
