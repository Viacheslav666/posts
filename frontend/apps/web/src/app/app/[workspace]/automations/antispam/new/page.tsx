import { RuleCreatePage } from '@/features/antispam/components/rule-create-page';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function AntispamNewPage({ params }: PageProps) {
  const { workspace } = await params;
  return <RuleCreatePage workspaceId={workspace} />;
}
