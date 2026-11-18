import { StoriesListPage } from '@/features/stories/components/stories-list-page';

export default async function StoriesPage({
  params,
}: {
  params: Promise<{ workspace: string }>;
}) {
  const { workspace } = await params;
  return <StoriesListPage workspaceId={workspace} />;
}
