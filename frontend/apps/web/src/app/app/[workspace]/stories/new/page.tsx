import { StoryEditor } from '@/features/stories/components/story-editor';

export default async function NewStoryPage({
  params,
}: {
  params: Promise<{ workspace: string }>;
}) {
  const { workspace } = await params;
  return <StoryEditor workspaceId={workspace} />;
}
