import { RuleDetailPage } from '@/features/grabber/components/rule-detail-page';

type RuleRouteProps = {
  params: Promise<{ workspace: string; ruleId: string }>;
};

export default async function WorkspaceGrabberRulePage({ params }: RuleRouteProps) {
  const { workspace, ruleId } = await params;
  return <RuleDetailPage workspaceId={workspace} ruleId={ruleId} />;
}
