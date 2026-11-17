import { TeamPage } from '@/features/team/components/team-page';

type TeamRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceTeamPage({ params }: TeamRouteProps) {
  const { workspace } = await params;
  return <TeamPage workspaceId={workspace} />;
}
