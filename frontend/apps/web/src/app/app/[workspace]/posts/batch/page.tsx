import { PostsBatchPage } from '@/features/posts/components/posts-batch-page';

export default async function PostsBatchRoute({
  params,
}: {
  params: Promise<{ workspace: string }>;
}) {
  const { workspace } = await params;
  return <PostsBatchPage workspaceId={workspace} />;
}
