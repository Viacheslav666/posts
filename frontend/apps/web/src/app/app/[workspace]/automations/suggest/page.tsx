import { SuggestPage } from '@/features/suggest/components/suggest-page';

type PageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function SuggestAutomationsPage({ params }: PageProps) {
  const { workspace } = await params;
  return <SuggestPage workspaceId={workspace} />;
}
