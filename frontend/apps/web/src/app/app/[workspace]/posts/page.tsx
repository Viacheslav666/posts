import { Suspense } from 'react';

import { PostsListPage } from '@/features/posts/components/posts-list-page';

type PostsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspacePostsPage({ params }: PostsRouteProps) {
  const { workspace } = await params;
  return (
    <Suspense fallback={<div className="p-6 text-sm text-muted-foreground">Загрузка…</div>}>
      <PostsListPage workspaceId={workspace} />
    </Suspense>
  );
}
