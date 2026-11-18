import { SettingsHub } from '@/features/settings/components/settings-hub';

type SettingsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceSettingsPage({ params }: SettingsRouteProps) {
  const { workspace } = await params;
  return <SettingsHub workspaceId={workspace} />;
}
