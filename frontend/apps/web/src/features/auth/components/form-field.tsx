import { cn, Input } from '@posts/ui';
import type { InputHTMLAttributes, ReactNode } from 'react';
import { forwardRef } from 'react';

type FormFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: ReactNode;
};

export const FormField = forwardRef<HTMLInputElement, FormFieldProps>(
  ({ id, label, error, hint, className, ...props }, ref) => {
    const fieldId = id ?? props.name;
    const errorId = fieldId ? `${fieldId}-error` : undefined;
    const hintId = fieldId ? `${fieldId}-hint` : undefined;

    return (
      <div className="space-y-1.5">
        <label htmlFor={fieldId} className="text-sm font-medium text-foreground">
          {label}
        </label>
        <Input
          id={fieldId}
          ref={ref}
          aria-invalid={Boolean(error) || undefined}
          aria-describedby={[error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') || undefined}
          className={cn(error && 'border-destructive focus-visible:ring-destructive', className)}
          {...props}
        />
        {hint ? (
          <p id={hintId} className="text-xs text-muted-foreground">
            {hint}
          </p>
        ) : null}
        {error ? (
          <p id={errorId} role="alert" className="text-xs text-destructive">
            {error}
          </p>
        ) : null}
      </div>
    );
  },
);
FormField.displayName = 'FormField';
