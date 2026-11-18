import { OnboardingPage } from '@/features/onboarding/components/onboarding-page';

type OnboardingRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function OnboardingRoute({ params }: OnboardingRouteProps) {
  const { workspace } = await params;
  return <OnboardingPage workspaceId={workspace} />;
}
