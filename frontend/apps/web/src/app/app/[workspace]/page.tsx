import { DashboardPage } from '@/features/dashboard/components/dashboard-page';

type WorkspacePageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceDashboardPage({ params }: WorkspacePageProps) {
  const { workspace } = await params;
  return <DashboardPage workspaceId={workspace} />;
}
