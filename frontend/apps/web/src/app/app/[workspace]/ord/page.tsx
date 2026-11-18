import { OrdSettingsPanel } from '@/features/ord/components/ord-settings-panel';

type OrdSettingsPageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function OrdSettingsPage({ params }: OrdSettingsPageProps) {
  const { workspace } = await params;
  return <OrdSettingsPanel workspaceId={workspace} />;
}