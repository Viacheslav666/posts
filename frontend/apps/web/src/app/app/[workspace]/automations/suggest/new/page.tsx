import { SettingsCreatePage } from '@/features/suggest/components/settings-create-page';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function SuggestNewPage({ params }: PageProps) {
  const { workspace } = await params;
  return <SettingsCreatePage workspaceId={workspace} />;
}
