'use client';

import { useTranslations } from 'next-intl';
import Link from 'next/link';

import { SettingsShell } from './settings-shell';

type SettingsHubProps = {
  workspaceId: string;
};

const CARDS = [
  { key: 'profile', href: 'profile' },
  { key: 'notifications', href: 'notifications' },
  { key: 'security', href: 'security' },
  { key: 'workspace', href: 'workspace' },
] as const;

export function SettingsHub({ workspaceId }: SettingsHubProps) {
  const t = useTranslations('settings');

  return (
    <SettingsShell workspaceId={workspaceId} title={t('hub.title')} description={t('hub.description')}>
      <ul className="grid gap-3 sm:grid-cols-2" data-testid="settings-hub">
        {CARDS.map((card) => (
          <li key={card.key}>
            <Link
              href={`/app/${workspaceId}/settings/${card.href}`}
              className="block rounded-md border border-border px-4 py-4 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <p className="font-medium text-foreground">{t(`hub.cards.${card.key}.title`)}</p>
              <p className="mt-1 text-sm text-muted-foreground">
                {t(`hub.cards.${card.key}.body`)}
              </p>
            </Link>
          </li>
        ))}
      </ul>
    </SettingsShell>
  );
}
