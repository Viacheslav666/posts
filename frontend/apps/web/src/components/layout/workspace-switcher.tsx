'use client';

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';

const STUB_WORKSPACES = [
  { id: 'demo', name: 'Demo Agency' },
  { id: 'acme', name: 'Acme Brand' },
] as const;

type WorkspaceSwitcherProps = {
  currentWorkspaceId: string;
};

export function WorkspaceSwitcher({ currentWorkspaceId }: WorkspaceSwitcherProps) {
  const t = useTranslations('workspaceSwitcher');
  const router = useRouter();
  const value = STUB_WORKSPACES.some((ws) => ws.id === currentWorkspaceId)
    ? currentWorkspaceId
    : STUB_WORKSPACES[0].id;

  return (
    <div className="space-y-1">
      <label className="px-1 text-xs font-medium text-muted-foreground" htmlFor="workspace-switcher">
        {t('label')}
      </label>
      <Select
        value={value}
        onValueChange={(next) => {
          router.push(`/app/${next}`);
        }}
      >
        <SelectTrigger id="workspace-switcher" aria-label={t('label')} className="w-full">
          <SelectValue placeholder={t('placeholder')} />
        </SelectTrigger>
        <SelectContent>
          {STUB_WORKSPACES.map((workspace) => (
            <SelectItem key={workspace.id} value={workspace.id}>
              {workspace.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <p className="px-1 text-[11px] leading-snug text-muted-foreground">{t('stubHint')}</p>
    </div>
  );
}
