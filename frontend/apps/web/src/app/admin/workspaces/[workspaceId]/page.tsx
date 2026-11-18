import { AdminWorkspaceDetailPage } from '@/features/admin/components/workspace-detail-page';

type PageProps = {
  params: Promise<{ workspaceId: string }>;
};

export default async function Page({ params }: PageProps) {
  const { workspaceId } = await params;
  return <AdminWorkspaceDetailPage workspaceId={workspaceId} />;
}
