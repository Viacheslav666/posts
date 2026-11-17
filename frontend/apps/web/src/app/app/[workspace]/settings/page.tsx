import { WorkspaceSettingsPanel } from '@/features/workspace/components/workspace-settings-panel';

type SettingsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceSettingsPage({ params }: SettingsRouteProps) {
  const { workspace } = await params;
  return <WorkspaceSettingsPanel workspaceId={workspace} />;
}
