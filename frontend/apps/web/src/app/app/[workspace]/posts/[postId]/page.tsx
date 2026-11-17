import Link from 'next/link';
import { getTranslations } from 'next-intl/server';

import { PostDetailPanel } from '@/features/posts/components/post-detail-panel';

type PostDetailRouteProps = {
  params: Promise<{ workspace: string; postId: string }>;
};

export default async function WorkspacePostDetailPage({ params }: PostDetailRouteProps) {
  const { workspace, postId } = await params;
  const t = await getTranslations('posts');

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <Link
        href={`/app/${workspace}/posts`}
        className="text-sm text-muted-foreground underline-offset-2 hover:underline"
      >
        ← {t('backToList')}
      </Link>
      <PostDetailPanel workspaceId={workspace} postId={postId} />
    </div>
  );
}
