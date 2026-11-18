import { BillingPage } from '@/features/billing/components/billing-page';

type BillingRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function BillingRoute({ params }: BillingRouteProps) {
  const { workspace } = await params;
  return <BillingPage workspaceId={workspace} />;
}
