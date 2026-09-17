import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';

interface FormControlProps {
  id?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
}

interface FormFieldProps {
  label: ReactNode;
  children: ReactElement<FormControlProps>;
  hint?: ReactNode;
  error?: ReactNode;
  required?: boolean;
}

export function FormField({ label, children, hint, error, required = false }: FormFieldProps) {
  const generatedId = useId();
  const controlId = children.props.id ?? generatedId;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [children.props['aria-describedby'], hintId, errorId].filter(Boolean).join(' ') || undefined;

  if (!isValidElement(children)) return null;

  return (
    <div className="ui-form-field">
      <label htmlFor={controlId} className="ui-form-field__label">
        {label}{required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {cloneElement(children, {
        id: controlId,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : children.props['aria-invalid'],
      })}
      {hint ? <div id={hintId} className="ui-form-field__hint">{hint}</div> : null}
      {error ? <div id={errorId} className="ui-form-field__error">{error}</div> : null}
    </div>
  );
}
