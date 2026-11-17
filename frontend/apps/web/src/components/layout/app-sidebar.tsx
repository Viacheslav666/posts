'use client';

import { cn } from '@posts/ui';
import {
  BarChart3,
  Bell,
  CalendarDays,
  CreditCard,
  FolderKanban,
  FolderOpen,
  Inbox,
  LayoutDashboard,
  Megaphone,
  Radio,
  Settings,
  Users,
  Workflow,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

import { WorkspaceSwitcher } from './workspace-switcher';

type AppSidebarProps = {
  workspaceId: string;
  className?: string;
};

const NAV_ITEMS = [
  { key: 'dashboard', href: '', icon: LayoutDashboard },
  { key: 'calendar', href: '/calendar', icon: CalendarDays },
  { key: 'posts', href: '/posts', icon: Megaphone },
  { key: 'channels', href: '/channels', icon: Radio },
  { key: 'media', href: '/media', icon: FolderOpen },
  { key: 'analytics', href: '/analytics', icon: BarChart3 },
  { key: 'inbox', href: '/inbox', icon: Inbox },
  { key: 'automations', href: '/automations', icon: Workflow },
  { key: 'ord', href: '/ord', icon: Bell },
  { key: 'projects', href: '/projects', icon: FolderKanban },
  { key: 'team', href: '/team', icon: Users },
  { key: 'settings', href: '/settings', icon: Settings },
  { key: 'billing', href: '/billing', icon: CreditCard },
] as const;

export function AppSidebar({ workspaceId, className }: AppSidebarProps) {
  const t = useTranslations('nav');
  const pathname = usePathname();
  const base = `/app/${workspaceId}`;

  return (
    <aside
      className={cn(
        'flex h-full w-sidebar flex-col border-r border-border bg-card/80 backdrop-blur-sm',
        className,
      )}
      aria-label="Sidebar"
    >
      <div className="border-b border-border p-4">
        <WorkspaceSwitcher currentWorkspaceId={workspaceId} />
      </div>
      <nav className="flex-1 space-y-1 overflow-y-auto p-3" aria-label="Main">
        {NAV_ITEMS.map((item) => {
          const href = `${base}${item.href}`;
          const isActive =
            item.href === ''
              ? pathname === base || pathname === `${base}/`
              : pathname === href || pathname.startsWith(`${href}/`);
          const Icon = item.icon;

          return (
            <Link
              key={item.key}
              href={href}
              className={cn(
                'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                isActive
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
              aria-current={isActive ? 'page' : undefined}
            >
              <Icon className="h-4 w-4 shrink-0" aria-hidden />
              <span>{t(item.key)}</span>
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
