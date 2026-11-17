import type { ReactNode } from 'react';

import { AppShell } from '@/components/layout/app-shell';

type WorkspaceLayoutProps = {
  children: ReactNode;
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceLayout({ children, params }: WorkspaceLayoutProps) {
  const { workspace } = await params;

  return <AppShell workspaceId={workspace}>{children}</AppShell>;
}
