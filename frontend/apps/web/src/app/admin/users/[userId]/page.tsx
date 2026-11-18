import { AdminUserDetailPage } from '@/features/admin/components/user-detail-page';

type PageProps = {
  params: Promise<{ userId: string }>;
};

export default async function Page({ params }: PageProps) {
  const { userId } = await params;
  return <AdminUserDetailPage userId={userId} />;
}
