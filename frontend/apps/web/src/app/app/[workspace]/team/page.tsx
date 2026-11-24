import { TeamPage } from '@/features/team/components/team-page';

type TeamRouteProps = {
  params: Promise<{ workspace: string }>;
  searchParams: Promise<{ tab?: string | string[] }>;
};

export default async function WorkspaceTeamPage({ params, searchParams }: TeamRouteProps) {
  const { workspace } = await params;
  const query = await searchParams;
  const raw = query.tab;
  const tab = (Array.isArray(raw) ? raw[0] : raw) === 'invitations' ? 'invitations' : 'members';
  return <TeamPage workspaceId={workspace} initialTab={tab} />;
}
