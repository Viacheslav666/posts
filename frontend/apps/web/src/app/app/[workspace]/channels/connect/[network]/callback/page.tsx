import { Suspense } from 'react';

import { OauthCallbackPage } from '@/features/channels/components/oauth-callback-page';

type CallbackRouteProps = {
  params: Promise<{ workspace: string; network: string }>;
};

export default async function ChannelOauthCallbackRoute({ params }: CallbackRouteProps) {
  const { workspace, network } = await params;
  return (
    <Suspense fallback={<p className="text-sm text-muted-foreground">…</p>}>
      <OauthCallbackPage workspaceId={workspace} network={network} />
    </Suspense>
  );
}
