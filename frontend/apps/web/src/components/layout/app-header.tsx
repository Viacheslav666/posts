'use client';

import {
  Avatar,
  AvatarFallback,
  Button,
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@posts/ui';
import { Bell, Menu, Plus, Search } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';

import { AppSidebar } from './app-sidebar';
import { ThemeToggle } from './theme-toggle';

type AppHeaderProps = {
  workspaceId: string;
};

export function AppHeader({ workspaceId }: AppHeaderProps) {
  const t = useTranslations('common');

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
        <Button variant="ghost" size="icon" aria-label={t('search')} className="hidden sm:inline-flex">
          <Search aria-hidden />
        </Button>
        <Button className="hidden sm:inline-flex" size="sm">
          <Plus aria-hidden />
          {t('createPost')}
        </Button>
        <Button variant="ghost" size="icon" aria-label={t('notifications')}>
          <Bell aria-hidden />
        </Button>
        <ThemeToggle />
        <Avatar className="h-9 w-9" aria-label={t('profile')}>
          <AvatarFallback>П</AvatarFallback>
        </Avatar>
      </div>
    </header>
  );
}
