import { StoriesBatchPage } from '@/features/stories/components/stories-batch-page';

export default async function StoriesBatchRoute({
  params,
}: {
  params: Promise<{ workspace: string }>;
}) {
  const { workspace } = await params;
  return <StoriesBatchPage workspaceId={workspace} />;
}
