import type { ReactNode } from 'react';

type LegalStubProps = {
  title: string;
  body: string;
  children?: ReactNode;
};

export function LegalStub({ title, body, children }: LegalStubProps) {
  return (
    <main id="main-content" className="mx-auto w-full max-w-3xl px-4 pb-20 pt-28 sm:px-6 lg:px-8">
      <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">{title}</h1>
      <p className="mt-4 text-base leading-relaxed text-muted-foreground">{body}</p>
      {children}
    </main>
  );
}
