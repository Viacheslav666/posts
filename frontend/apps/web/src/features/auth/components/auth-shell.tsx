import Link from 'next/link';
import type { ReactNode } from 'react';

type AuthShellProps = {
  brand: string;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
};

export function AuthShell({ brand, title, description, children, footer }: AuthShellProps) {
  return (
    <main
      id="main-content"
      className="relative mx-auto flex min-h-screen w-full max-w-md flex-col justify-center px-4 py-12 sm:px-6"
    >
      <div className="auth-panel animate-auth-in rounded-xl border border-border/70 bg-background/80 p-6 shadow-md backdrop-blur-sm sm:p-8">
        <Link href="/" className="font-display text-3xl font-bold tracking-tight text-foreground">
          {brand}
        </Link>
        <h1 className="mt-6 text-xl font-semibold text-foreground sm:text-2xl">{title}</h1>
        {description ? <p className="mt-2 text-sm text-muted-foreground">{description}</p> : null}
        <div className="mt-6">{children}</div>
        {footer ? <div className="mt-6 text-sm text-muted-foreground">{footer}</div> : null}
      </div>
    </main>
  );
}
