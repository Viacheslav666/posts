import { ProjectsPanel } from '@/features/workspace/components/projects-panel';

type ProjectsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceProjectsPage({ params }: ProjectsRouteProps) {
  const { workspace } = await params;
  return <ProjectsPanel workspaceId={workspace} />;
}
