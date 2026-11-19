import { RuleDetailPage } from '@/features/crosspost/components/rule-detail-page';

type PageProps = {
  params: Promise<{ workspace: string; id: string }>;
};

export default async function CrosspostDetailPage({ params }: PageProps) {
  const { workspace, id } = await params;
  return <RuleDetailPage workspaceId={workspace} ruleId={id} />;
}
