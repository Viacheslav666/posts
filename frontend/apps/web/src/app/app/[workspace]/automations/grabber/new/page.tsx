import { RuleCreatePage } from '@/features/grabber/components/rule-create-page';

type NewRuleRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceGrabberNewPage({ params }: NewRuleRouteProps) {
  const { workspace } = await params;
  return <RuleCreatePage workspaceId={workspace} />;
}
