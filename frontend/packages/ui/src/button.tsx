import type { ButtonHTMLAttributes, PropsWithChildren } from 'react';

export type ButtonProps = PropsWithChildren<ButtonHTMLAttributes<HTMLButtonElement>> & {
  variant?: 'primary' | 'secondary';
};

export function Button({ children, variant = 'primary', className = '', ...props }: ButtonProps) {
  const base =
    'inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2';
  const variants = {
    primary: 'bg-slate-900 text-white hover:bg-slate-800 focus-visible:outline-slate-900',
    secondary: 'bg-slate-100 text-slate-900 hover:bg-slate-200 focus-visible:outline-slate-400',
  };

  return (
    <button className={`${base} ${variants[variant]} ${className}`.trim()} type="button" {...props}>
      {children}
    </button>
  );
}
