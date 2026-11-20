'use client';

import type { HTMLAttributes, ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';
import {
  Avatar,
  AvatarFallback,
  Button,
} from '@posts/ui';
import { LogOut, Settings } from 'lucide-react';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { logoutRequest } from '@/features/auth/api/auth-api';
import { redirectToLogin } from '@/shared/lib/auth-redirect';
import { useMe } from '@/shared/hooks/use-me';

type UserAccountMenuProps = {
  workspaceId: string;
};

function MenuPanel({ className, children, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={className} {...rest}>
      {children}
    </div>
  );
}

export function UserAccountMenu({ workspaceId }: UserAccountMenuProps) {
  const t = useTranslations('common');
  const { data: me } = useMe();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const initials = (me?.name?.trim()?.charAt(0) || 'П').toUpperCase();

  useEffect(() => {
    if (!open) {
      return;
    }
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const logout = async () => {
    setOpen(false);
    try {
      await logoutRequest();
    } finally {
      redirectToLogin('logout');
    }
  };

  return (
    <div className="relative" ref={rootRef}>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="rounded-full"
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label={t('profile')}
        onClick={() => setOpen((value) => !value)}
        data-testid="user-account-menu-trigger"
      >
        <Avatar className="h-9 w-9">
          <AvatarFallback>{initials}</AvatarFallback>
        </Avatar>
      </Button>
      {open ? (
        <MenuPanel
          className="absolute right-0 top-full z-50 mt-2 w-48 rounded-md border border-border bg-popover p-1 shadow-md"
          role="menu"
          data-testid="user-account-menu"
        >
          <Link
            href={`/app/${workspaceId}/settings/profile`}
            className="flex items-center gap-2 rounded-sm px-2 py-2 text-sm hover:bg-accent"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <Settings className="h-4 w-4" aria-hidden />
            {t('profile')}
          </Link>
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-sm px-2 py-2 text-left text-sm hover:bg-accent"
            role="menuitem"
            data-testid="user-account-logout"
            onClick={() => void logout()}
          >
            <LogOut className="h-4 w-4" aria-hidden />
            {t('logout')}
          </button>
        </MenuPanel>
      ) : null}
    </div>
  );
}