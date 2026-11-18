'use client';

import { cn } from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const LINKS = [
  { key: 'hub', href: '' },
  { key: 'profile', href: '/profile' },
  { key: 'notifications', href: '/notifications' },
  { key: 'security', href: '/security' },
  { key: 'workspace', href: '/workspace' },
] as const;

type SettingsNavProps = {
  workspaceId: string;
};

export function SettingsNav({ workspaceId }: SettingsNavProps) {
  const t = useTranslations('settings.nav');
  const pathname = usePathname();
  const base = `/app/${workspaceId}/settings`;

  return (
    <nav className="flex flex-wrap gap-1" aria-label={t('label')} data-testid="settings-nav">
      {LINKS.map((link) => {
        const href = `${base}${link.href}`;
        const isActive =
          link.href === ''
            ? pathname === base || pathname === `${base}/`
            : pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={link.key}
            href={href}
            className={cn(
              'rounded-md px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              isActive
                ? 'bg-primary/10 text-primary'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
            aria-current={isActive ? 'page' : undefined}
          >
            {t(link.key)}
          </Link>
        );
      })}
    </nav>
  );
}
