import { SettingsDetailPage } from '@/features/suggest/components/settings-detail-page';

type PageProps = {
  params: Promise<{ workspace: string; id: string }>;
};

export default async function SuggestDetailPage({ params }: PageProps) {
  const { workspace, id } = await params;
  return <SettingsDetailPage workspaceId={workspace} settingsId={id} />;
}
