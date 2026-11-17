import type { ReactNode } from 'react';

import { AppHeader } from './app-header';
import { AppSidebar } from './app-sidebar';

type AppShellProps = {
  workspaceId: string;
  children: ReactNode;
};

export function AppShell({ workspaceId, children }: AppShellProps) {
  return (
    <div className="flex min-h-screen flex-col">
      <AppHeader workspaceId={workspaceId} />
      <div className="flex min-h-0 flex-1">
        <div className="hidden md:block">
          <div className="sticky top-header h-[calc(100vh-var(--header-height))]">
            <AppSidebar workspaceId={workspaceId} />
          </div>
        </div>
        <main id="main-content" className="min-w-0 flex-1 p-4 md:p-6" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
