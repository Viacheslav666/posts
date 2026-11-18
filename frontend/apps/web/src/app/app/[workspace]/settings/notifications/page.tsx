import { NotificationSettingsForm } from '@/features/notifications/components/notification-settings-form';

type NotificationSettingsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function NotificationSettingsRoute({
  params,
}: NotificationSettingsRouteProps) {
  const { workspace } = await params;
  return <NotificationSettingsForm workspaceId={workspace} />;
}
