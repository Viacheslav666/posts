import { GrabberPage } from '@/features/grabber/components/grabber-page';

type GrabberRouteProps = {
  params: Promise<{ workspace: string }>;
  searchParams: Promise<{ tab?: string | string[] }>;
};

export default async function WorkspaceGrabberPage({ params, searchParams }: GrabberRouteProps) {
  const { workspace } = await params;
  const query = await searchParams;
  const raw = query.tab;
  const tab = (Array.isArray(raw) ? raw[0] : raw) === 'rules' ? 'rules' : 'queue';
  return <GrabberPage workspaceId={workspace} initialTab={tab} />;
}
