'use client';

import { cn } from '@posts/ui';
import {
  Flag,
  LayoutList,
  Megaphone,
  ScrollText,
  Tags,
  Users,
  Building2,
  TicketPercent,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const NAV = [
  { key: 'users', href: '/admin/users', icon: Users },
  { key: 'workspaces', href: '/admin/workspaces', icon: Building2 },
  { key: 'plans', href: '/admin/plans', icon: Tags },
  { key: 'promos', href: '/admin/promos', icon: TicketPercent },
  { key: 'queues', href: '/admin/queues', icon: LayoutList },
  { key: 'featureFlags', href: '/admin/feature-flags', icon: Flag },
  { key: 'announcements', href: '/admin/announcements', icon: Megaphone },
  { key: 'audit', href: '/admin/audit', icon: ScrollText },
] as const;

export function AdminNav() {
  const t = useTranslations('admin.nav');
  const pathname = usePathname();

  return (
    <nav className="flex flex-col gap-1 p-3" aria-label={t('aria')} data-testid="admin-nav">
      {NAV.map((item) => {
        const href = item.href;
        const active = pathname === href || pathname.startsWith(`${href}/`);
        const Icon = item.icon;
        return (
          <Link
            key={item.key}
            href={href}
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              active
                ? 'bg-primary/10 text-primary'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            aria-current={active ? 'page' : undefined}
            data-testid={`admin-nav-${item.key}`}
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            <span>{t(item.key)}</span>
          </Link>
        );
      })}
    </nav>
  );
}
