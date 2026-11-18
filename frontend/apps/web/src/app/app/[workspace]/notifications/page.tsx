import { NotificationsPage } from '@/features/notifications/components/notifications-page';

type NotificationsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function NotificationsRoute({ params }: NotificationsRouteProps) {
  const { workspace } = await params;
  return <NotificationsPage workspaceId={workspace} />;
}
