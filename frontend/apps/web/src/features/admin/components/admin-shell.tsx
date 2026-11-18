'use client';

import { Button } from '@posts/ui';
import { ArrowLeft } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { useMe } from '@/shared/hooks/use-me';

import { AdminGuard } from './admin-guard';
import { AdminNav } from './admin-nav';

type AdminShellProps = {
  children: ReactNode;
};

export function AdminShell({ children }: AdminShellProps) {
  const t = useTranslations('admin');
  const { data: me } = useMe();
  const homeWorkspace = me?.memberships?.[0]?.workspaceId;

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-br from-slate-100 via-background to-slate-50 dark:from-slate-950 dark:via-background dark:to-slate-900">
      <header className="sticky top-0 z-40 border-b border-border bg-background/90 px-4 py-3 backdrop-blur md:px-6">
        <div className="mx-auto flex max-w-7xl items-center gap-3">
          <Link href="/admin" className="font-display text-xl font-bold tracking-tight text-foreground">
            {t('brand')}
          </Link>
          <span className="hidden text-sm text-muted-foreground sm:inline">{t('subtitle')}</span>
          <div className="ml-auto">
            {homeWorkspace ? (
              <Button variant="outline" size="sm" asChild>
                <Link href={`/app/${homeWorkspace}`} data-testid="admin-back-to-app">
                  <ArrowLeft aria-hidden className="h-4 w-4" />
                  {t('backToApp')}
                </Link>
              </Button>
            ) : null}
          </div>
        </div>
      </header>
      <div className="mx-auto flex w-full max-w-7xl flex-1">
        <aside className="hidden w-56 shrink-0 border-r border-border bg-card/60 md:block">
          <AdminNav />
        </aside>
        <main id="main-content" className="min-w-0 flex-1 p-4 md:p-6" tabIndex={-1}>
          <div className="mb-4 md:hidden">
            <AdminNav />
          </div>
          <AdminGuard>{children}</AdminGuard>
        </main>
      </div>
    </div>
  );
}
