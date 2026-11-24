'use client';

import { Avatar, AvatarFallback, AvatarImage, Button, Tooltip, TooltipContent, TooltipTrigger } from '@posts/ui';
import { LogOut, Settings, User, UserRound } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useId, useRef, useState } from 'react';

import { logoutRequest } from '@/features/auth/api/auth-api';
import { redirectToLogin } from '@/shared/lib/auth-redirect';
import { useMe } from '@/shared/hooks/use-me';

import { accountInitials, accountTooltip, isSocialLogin } from './account-initials';

type UserAccountMenuProps = {
  workspaceId: string;
};

export function UserAccountMenu({ workspaceId }: UserAccountMenuProps) {
  const { data: me } = useMe();
  const [open, setOpen] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const initials = accountInitials(me?.name, me?.email);
  const label = accountTooltip(me?.name, me?.email, me?.loginMethod);
  const showPhoto = isSocialLogin(me?.loginMethod) && Boolean(me?.avatarUrl) && !imageFailed;
  const canSwitch = (me?.memberships?.length ?? 0) > 1;

  useEffect(() => {
    setImageFailed(false);
  }, [me?.avatarUrl, me?.loginMethod]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocClick);
      document.removeEventListener('keydown', onKey);
    };
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
      <Tooltip>
        <TooltipTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className={`h-9 w-9 shrink-0 rounded-full ${open ? 'bg-accent ring-2 ring-ring' : ''}`}
            aria-expanded={open}
            aria-haspopup="menu"
            aria-controls={open ? menuId : undefined}
            aria-label={label}
            title={label}
            onClick={() => setOpen((value) => !value)}
            data-testid="user-account-menu-trigger"
          >
            <Avatar className="h-9 w-9">
              {showPhoto ? (
                <AvatarImage
                  src={me?.avatarUrl ?? undefined}
                  alt=""
                  onLoadingStatusChange={(status) => {
                    if (status === 'error') {
                      setImageFailed(true);
                    }
                  }}
                />
              ) : null}
              <AvatarFallback className="bg-primary text-xs font-semibold text-primary-foreground">
                {initials ?? <User className="h-4 w-4" aria-hidden />}
              </AvatarFallback>
            </Avatar>
          </Button>
        </TooltipTrigger>
        <TooltipContent>{label}</TooltipContent>
      </Tooltip>
      {open ? (
        <div
          id={menuId}
          className="absolute left-0 top-full z-50 mt-2 w-56 rounded-md border border-border bg-popover p-1 shadow-md"
          role="menu"
          aria-label="Аккаунт"
          data-testid="user-account-menu"
          onKeyDown={(event) => {
            const items = event.currentTarget.querySelectorAll<HTMLElement>('[role="menuitem"]');
            const current = Array.from(items).indexOf(document.activeElement as HTMLElement);
            if (event.key === 'ArrowDown') {
              event.preventDefault();
              items[(current + 1 + items.length) % items.length]?.focus();
            } else if (event.key === 'ArrowUp') {
              event.preventDefault();
              items[(current - 1 + items.length) % items.length]?.focus();
            } else if (event.key === 'Escape') {
              event.preventDefault();
              setOpen(false);
            }
          }}
        >
          <Link
            href={`/app/${workspaceId}/settings/profile`}
            className="flex items-center gap-2 rounded-sm px-2 py-2 text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <UserRound className="h-4 w-4" aria-hidden />
            Профиль
          </Link>
          <Link
            href={`/app/${workspaceId}/settings/security`}
            className="flex items-center gap-2 rounded-sm px-2 py-2 text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <Settings className="h-4 w-4" aria-hidden />
            Настройки аккаунта
          </Link>
          {canSwitch ? (
            <button
              type="button"
              className="flex w-full items-center gap-2 rounded-sm px-2 py-2 text-left text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              role="menuitem"
              onClick={() => {
                setOpen(false);
                document.getElementById('workspace-switcher')?.focus();
              }}
            >
              Сменить пространство
            </button>
          ) : null}
          <button
            type="button"
            className="flex w-full items-center gap-2 rounded-sm px-2 py-2 text-left text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            role="menuitem"
            data-testid="user-account-logout"
            onClick={() => void logout()}
          >
            <LogOut className="h-4 w-4" aria-hidden />
            Выйти
          </button>
        </div>
      ) : null}
    </div>
  );
}
