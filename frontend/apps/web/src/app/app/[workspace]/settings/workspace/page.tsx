import { SettingsNav } from '@/features/settings/components/settings-nav';
import { WorkspaceSettingsPanel } from '@/features/workspace/components/workspace-settings-panel';

type WorkspaceSettingsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceSettingsRoute({ params }: WorkspaceSettingsRouteProps) {
  const { workspace } = await params;
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" data-testid="settings-workspace">
      <SettingsNav workspaceId={workspace} />
      <WorkspaceSettingsPanel workspaceId={workspace} />
    </div>
  );
}
