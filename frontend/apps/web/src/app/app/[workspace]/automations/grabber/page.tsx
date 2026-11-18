import { GrabberPage } from '@/features/grabber/components/grabber-page';

type GrabberRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceGrabberPage({ params }: GrabberRouteProps) {
  const { workspace } = await params;
  return <GrabberPage workspaceId={workspace} />;
}
