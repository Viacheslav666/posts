import { Suspense } from 'react';

import { OauthCallbackPage } from '@/features/channels/components/oauth-callback-page';

export default function VkChannelStaticConnectCallbackRoute() {
  return (
    <Suspense fallback={<p className="text-sm text-muted-foreground">…</p>}>
      <OauthCallbackPage network="vk" />
    </Suspense>
  );
}