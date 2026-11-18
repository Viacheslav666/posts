import { SecuritySettingsPanel } from '@/features/settings/components/security-settings-panel';

type SecuritySettingsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function SecuritySettingsRoute({ params }: SecuritySettingsRouteProps) {
  const { workspace } = await params;
  return <SecuritySettingsPanel workspaceId={workspace} />;
}
