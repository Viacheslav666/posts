'use client';

import {
  Button,
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@posts/ui';
import { Menu, Plus, Search, Shield } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';

import { HeaderPlanChip } from '@/features/billing/components/header-plan-chip';
import { NotificationsBell } from '@/features/notifications/components/notifications-bell';
import { useMe } from '@/shared/hooks/use-me';

import { AppSidebar } from './app-sidebar';
import { ThemeToggle } from './theme-toggle';
import { UserAccountMenu } from './user-account-menu';

type AppHeaderProps = {
  workspaceId: string;
};

export function AppHeader({ workspaceId }: AppHeaderProps) {
  const t = useTranslations('common');
  const { data: me } = useMe();
  const showAdmin = Boolean(me?.platformAdmin) && !me?.impersonation?.active;

  return (
    <header className="sticky top-0 z-40 flex h-header items-center gap-3 border-b border-border bg-background/90 px-4 backdrop-blur md:px-6">
      <div className="flex items-center gap-2 md:hidden">
        <Sheet>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" aria-label={t('menu')}>
              <Menu aria-hidden />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-sidebar p-0">
            <SheetHeader className="sr-only">
              <SheetTitle>{t('menu')}</SheetTitle>
            </SheetHeader>
            <AppSidebar workspaceId={workspaceId} className="border-0" />
          </SheetContent>
        </Sheet>
      </div>

      <Link href={`/app/${workspaceId}`} className="font-display text-xl font-bold tracking-tight text-foreground">
        {t('brand')}
      </Link>

      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        <HeaderPlanChip workspaceId={workspaceId} />
        {showAdmin ? (
          <Button variant="ghost" size="sm" className="hidden sm:inline-flex" asChild>
            <Link href="/admin" data-testid="header-admin-link" aria-label={t('adminPanel')}>
              <Shield aria-hidden className="h-4 w-4" />
              {t('adminPanel')}
            </Link>
          </Button>
        ) : null}
        <Button variant="ghost" size="icon" aria-label={t('search')} className="hidden sm:inline-flex">
          <Search aria-hidden />
        </Button>
        <Button className="hidden sm:inline-flex" size="sm" asChild>
          <Link href={`/app/${workspaceId}/posts/new`}>
            <Plus aria-hidden />
            {t('createPost')}
          </Link>
        </Button>
        <NotificationsBell workspaceId={workspaceId} />
        <ThemeToggle />
        <UserAccountMenu workspaceId={workspaceId} />
      </div>
    </header>
  );
}
