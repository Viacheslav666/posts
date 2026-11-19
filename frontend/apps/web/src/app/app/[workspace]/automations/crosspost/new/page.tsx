import { RuleCreatePage } from '@/features/crosspost/components/rule-create-page';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function CrosspostNewPage({ params }: PageProps) {
  const { workspace } = await params;
  return <RuleCreatePage workspaceId={workspace} />;
}
