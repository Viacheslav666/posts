import { ProfileSettingsForm } from '@/features/settings/components/profile-settings-form';

type ProfileSettingsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function ProfileSettingsRoute({ params }: ProfileSettingsRouteProps) {
  const { workspace } = await params;
  return <ProfileSettingsForm workspaceId={workspace} />;
}
