import { PostEditor } from '@/features/editor/components/post-editor';

type NewPostPageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function NewPostPage({ params }: NewPostPageProps) {
  const { workspace } = await params;
  return <PostEditor workspaceId={workspace} draftId="new" />;
}
