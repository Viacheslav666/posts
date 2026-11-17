'use client';

import { Button } from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';

import { ThemeToggle } from './theme-toggle';

export function PublicHeader() {
  const t = useTranslations('common');
  const nav = useTranslations('publicNav');

  return (
    <header className="fixed inset-x-0 top-0 z-40 border-b border-border/60 bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-4 px-4 sm:px-6 lg:px-8">
        <Link href="/" className="font-display text-xl font-bold tracking-tight text-foreground">
          {t('brand')}
        </Link>
        <nav className="ml-2 hidden items-center gap-4 text-sm font-medium text-muted-foreground sm:flex">
          <Link href="/pricing" className="hover:text-foreground">
            {nav('pricing')}
          </Link>
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
            <Link href="/login">{nav('login')}</Link>
          </Button>
          <Button asChild size="sm">
            <Link href="/register">{nav('register')}</Link>
          </Button>
        </div>
      </div>
    </header>
  );
}
