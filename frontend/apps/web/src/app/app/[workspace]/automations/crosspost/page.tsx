import { CrosspostPage } from '@/features/crosspost/components/crosspost-page';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function CrosspostAutomationsPage({ params }: PageProps) {
  const { workspace } = await params;
  return <CrosspostPage workspaceId={workspace} />;
}
