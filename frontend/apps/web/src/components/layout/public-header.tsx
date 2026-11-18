'use client';

import {
  Button,
  Sheet,
  SheetClose,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@posts/ui';
import { Menu } from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useState } from 'react';

import { ThemeToggle } from './theme-toggle';

export function PublicHeader() {
  const t = useTranslations('common');
  const nav = useTranslations('publicNav');
  const [open, setOpen] = useState(false);

  const links = [
    { href: '/#features', label: nav('features') },
    { href: '/pricing', label: nav('pricing') },
    { href: '/#how', label: nav('how') },
    { href: '/#faq', label: nav('faq') },
  ];

  return (
    <header className="fixed inset-x-0 top-0 z-40 border-b border-border/60 bg-background/80 backdrop-blur">
      <div className="mx-auto flex h-14 w-full max-w-6xl items-center gap-4 px-4 sm:px-6 lg:px-8">
        <Link href="/" className="font-display text-xl font-bold tracking-tight text-foreground">
          {t('brand')}
        </Link>
        <nav
          className="ml-2 hidden items-center gap-4 text-sm font-medium text-muted-foreground md:flex"
          aria-label={nav('menu')}
        >
          {links.map((link) => (
            <Link key={link.href} href={link.href} className="hover:text-foreground">
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <ThemeToggle />
          <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
            <Link href="/login" data-testid="public-login">
              {nav('login')}
            </Link>
          </Button>
          <Button asChild size="sm" className="hidden xs:inline-flex sm:inline-flex">
            <Link href="/register">{nav('register')}</Link>
          </Button>

          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger asChild>
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="sm:hidden"
                aria-label={nav('menu')}
                data-testid="public-menu-trigger"
              >
                <Menu className="size-4" aria-hidden />
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-[min(100%,20rem)]" data-testid="public-mobile-menu">
              <SheetHeader>
                <SheetTitle>{nav('menu')}</SheetTitle>
              </SheetHeader>
              <nav className="mt-8 flex flex-col gap-4" aria-label={nav('menu')}>
                {links.map((link) => (
                  <SheetClose asChild key={link.href}>
                    <Link
                      href={link.href}
                      className="text-base font-medium text-foreground"
                      onClick={() => setOpen(false)}
                    >
                      {link.label}
                    </Link>
                  </SheetClose>
                ))}
                <SheetClose asChild>
                  <Link
                    href="/login"
                    className="text-base font-medium text-foreground"
                    data-testid="public-mobile-login"
                    onClick={() => setOpen(false)}
                  >
                    {nav('login')}
                  </Link>
                </SheetClose>
                <SheetClose asChild>
                  <Button asChild>
                    <Link href="/register" onClick={() => setOpen(false)}>
                      {nav('register')}
                    </Link>
                  </Button>
                </SheetClose>
              </nav>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
}
