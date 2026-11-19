import { AntispamPage } from '@/features/antispam/components/antispam-page';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function AntispamAutomationsPage({ params }: PageProps) {
  const { workspace } = await params;
  return <AntispamPage workspaceId={workspace} />;
}
