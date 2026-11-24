import { StoriesListPage } from '@/features/stories/components/stories-list-page';

export default async function StoriesPage({
  params,
  searchParams,
}: {
  params: Promise<{ workspace: string }>;
  searchParams: Promise<{ status?: string | string[] }>;
}) {
  const { workspace } = await params;
  const query = await searchParams;
  const raw = query.status;
  const status = Array.isArray(raw) ? raw[0] : raw;
  return <StoriesListPage workspaceId={workspace} initialStatus={status} />;
}
