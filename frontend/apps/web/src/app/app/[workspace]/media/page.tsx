import { MediaLibraryPage } from '@/features/media/components/media-library-page';

type MediaRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceMediaPage({ params }: MediaRouteProps) {
  const { workspace } = await params;
  return <MediaLibraryPage workspaceId={workspace} />;
}
