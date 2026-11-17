import { ChannelsPage } from '@/features/channels/components/channels-page';

type ChannelsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceChannelsPage({ params }: ChannelsRouteProps) {
  const { workspace } = await params;
  return <ChannelsPage workspaceId={workspace} />;
}
