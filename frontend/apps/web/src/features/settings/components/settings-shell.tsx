'use client';

import type { ReactNode } from 'react';

import { SettingsNav } from './settings-nav';

type SettingsShellProps = {
  workspaceId: string;
  title: string;
  description?: string;
  children: ReactNode;
};

export function SettingsShell({ workspaceId, title, description, children }: SettingsShellProps) {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" data-testid="settings-shell">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">{title}</h1>
        {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
      </div>
      <SettingsNav workspaceId={workspaceId} />
      {children}
    </div>
  );
}
