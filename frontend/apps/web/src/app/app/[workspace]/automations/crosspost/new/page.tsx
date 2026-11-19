import { AutomationSectionStub } from '@/features/automations-common/components/automation-section-stub';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function CrosspostNewPage({ params }: PageProps) {
  const { workspace } = await params;
  return <AutomationSectionStub workspaceId={workspace} section="crosspost" />;
}
