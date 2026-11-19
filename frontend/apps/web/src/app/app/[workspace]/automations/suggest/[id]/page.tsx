import { AutomationSectionStub } from '@/features/automations-common/components/automation-section-stub';

type PageProps = {
  params: Promise<{ workspace: string; id: string }>;
};

export default async function SuggestDetailPage({ params }: PageProps) {
  const { workspace } = await params;
  return <AutomationSectionStub workspaceId={workspace} section="suggest" />;
}
