'use client';

import type { InputHTMLAttributes } from 'react';

type CheckboxRowProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
};

export function CheckboxRow({ label, className, id, ...props }: CheckboxRowProps) {
  const inputId = id ?? props.name;
  return (
    <label
      htmlFor={inputId}
      className={`inline-flex items-start gap-2 text-sm ${className ?? ''}`}
    >
      <input id={inputId} type="checkbox" className="mt-0.5 h-4 w-4 shrink-0" {...props} />
      <span>{label}</span>
    </label>
  );
}
