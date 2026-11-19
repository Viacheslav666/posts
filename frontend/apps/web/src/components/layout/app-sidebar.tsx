'use client';

import { cn } from '@posts/ui';
import {
  BadgeCheck,
  Ban,
  BarChart3,
  CalendarDays,
  CreditCard,
  FolderKanban,
  FolderOpen,
  GitBranch,
  Inbox,
  LayoutDashboard,
  Megaphone,
  MessageSquarePlus,
  Radio,
  Settings,
  Sparkles,
  Users,
  Workflow,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useMemo, type ComponentType } from 'react';

import { useAutomationFeatureFlags } from '@/features/automations-common/hooks/use-feature-flag';
import { useCan } from '@/shared/hooks/use-can';

import { WorkspaceSwitcher } from './workspace-switcher';

type AppSidebarProps = {
  workspaceId: string;
  className?: string;
};

type NavItem = {
  key: string;
  href: string;
  icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
  permission?: 'analytics.view' | 'ord.view' | 'billing.view';
};

const NAV_ITEMS_BEFORE_AUTOMATIONS: NavItem[] = [
  { key: 'dashboard', href: '', icon: LayoutDashboard },
  { key: 'calendar', href: '/calendar', icon: CalendarDays },
  { key: 'posts', href: '/posts', icon: Megaphone },
  { key: 'stories', href: '/stories', icon: Sparkles },
  { key: 'channels', href: '/channels', icon: Radio },
  { key: 'media', href: '/media', icon: FolderOpen },
  { key: 'analytics', href: '/analytics', icon: BarChart3, permission: 'analytics.view' },
  { key: 'inbox', href: '/inbox', icon: Inbox },
];

const NAV_ITEMS_AFTER_AUTOMATIONS: NavItem[] = [
  { key: 'ord', href: '/ord', icon: BadgeCheck, permission: 'ord.view' },
  { key: 'projects', href: '/projects', icon: FolderKanban },
  { key: 'team', href: '/team', icon: Users },
  { key: 'settings', href: '/settings', icon: Settings },
  { key: 'billing', href: '/billing', icon: CreditCard, permission: 'billing.view' },
];

export function AppSidebar({ workspaceId, className }: AppSidebarProps) {
  const t = useTranslations('nav');
  const tAuto = useTranslations('automations');
  const pathname = usePathname();
  const base = `/app/${workspaceId}`;
  const canViewAnalytics = useCan('analytics.view', workspaceId);
  const canViewOrd = useCan('ord.view', workspaceId);
  const canViewBilling = useCan('billing.view', workspaceId);
  const canViewAutomations = useCan('automations.view', workspaceId);
  const flags = useAutomationFeatureFlags();

  const before = useMemo(() => {
    return NAV_ITEMS_BEFORE_AUTOMATIONS.filter((item) => {
      if (!item.permission) return true;
      if (item.permission === 'analytics.view') return canViewAnalytics;
      if (item.permission === 'ord.view') return canViewOrd;
      if (item.permission === 'billing.view') return canViewBilling;
      return true;
    });
  }, [canViewAnalytics, canViewBilling, canViewOrd]);

  const after = useMemo(() => {
    return NAV_ITEMS_AFTER_AUTOMATIONS.filter((item) => {
      if (!item.permission) return true;
      if (item.permission === 'analytics.view') return canViewAnalytics;
      if (item.permission === 'ord.view') return canViewOrd;
      if (item.permission === 'billing.view') return canViewBilling;
      return true;
    });
  }, [canViewAnalytics, canViewBilling, canViewOrd]);

  const automationChildren = useMemo(() => {
    const children: Array<{
      key: string;
      href: string;
      label: string;
      icon: typeof Workflow;
    }> = [
      {
        key: 'grabber',
        href: `${base}/automations/grabber`,
        label: tAuto('grabber.title'),
        icon: Workflow,
      },
    ];
    if (flags.suggest) {
      children.push({
        key: 'suggest',
        href: `${base}/automations/suggest`,
        label: tAuto('suggest.title'),
        icon: MessageSquarePlus,
      });
    }
    if (flags.antispam) {
      children.push({
        key: 'antispam',
        href: `${base}/automations/antispam`,
        label: tAuto('antispam.title'),
        icon: Ban,
      });
    }
    if (flags.crosspost) {
      children.push({
        key: 'crosspost',
        href: `${base}/automations/crosspost`,
        label: tAuto('crosspost.title'),
        icon: GitBranch,
      });
    }
    return children;
  }, [base, flags.antispam, flags.crosspost, flags.suggest, tAuto]);

  const automationsHref = `${base}/automations`;

  const renderLink = (item: NavItem) => {
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
        <span>{t(item.key as 'dashboard')}</span>
      </Link>
    );
  };

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
        {before.map(renderLink)}

        {canViewAutomations ? (
          <div className="pt-1" data-testid="nav-automations-group">
            <Link
              href={automationsHref}
              className={cn(
                'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                pathname === automationsHref
                  ? 'bg-primary/10 text-primary'
                  : 'text-muted-foreground hover:bg-muted hover:text-foreground',
              )}
              aria-current={pathname === automationsHref ? 'page' : undefined}
            >
              <Workflow className="h-4 w-4 shrink-0" aria-hidden />
              <span>{t('automations')}</span>
            </Link>
            <ul className="ml-4 mt-1 space-y-0.5 border-l border-border pl-2" role="list">
              {automationChildren.map((child) => {
                const Icon = child.icon;
                const isActive =
                  pathname === child.href || pathname.startsWith(`${child.href}/`);
                return (
                  <li key={child.key}>
                    <Link
                      href={child.href}
                      data-testid={`nav-automations-${child.key}`}
                      className={cn(
                        'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                        isActive
                          ? 'bg-primary/10 text-primary'
                          : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                      )}
                      aria-current={isActive ? 'page' : undefined}
                    >
                      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden />
                      <span>{child.label}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ) : null}

        {after.map(renderLink)}
      </nav>
    </aside>
  );
}
