import { AutomationsHub } from '@/features/automations-common/components/automations-hub';

type AutomationsPageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function AutomationsPage({ params }: AutomationsPageProps) {
  const { workspace } = await params;
  return <AutomationsHub workspaceId={workspace} />;
}
