import type { ReactNode } from 'react';

type LegalStubProps = {
  title: string;
  body: string;
  draftLabel: string;
  children?: ReactNode;
};

export function LegalStub({ title, body, draftLabel, children }: LegalStubProps) {
  return (
    <main id="main-content" className="mx-auto w-full max-w-3xl px-4 pb-20 pt-28 sm:px-6 lg:px-8">
      <p className="mb-4 inline-block rounded-md border border-amber-500/40 bg-amber-500/10 px-2 py-1 text-xs font-medium text-amber-800 dark:text-amber-200">
        {draftLabel}
      </p>
      <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">{title}</h1>
      <p className="mt-4 text-base leading-relaxed text-muted-foreground">{body}</p>
      {children}
    </main>
  );
}
