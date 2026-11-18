import { PostsListPage } from '@/features/posts/components/posts-list-page';

type PostsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspacePostsPage({ params }: PostsRouteProps) {
  const { workspace } = await params;
  return <PostsListPage workspaceId={workspace} />;
}
