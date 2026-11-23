import { getTranslations } from 'next-intl/server';
import { Suspense } from 'react';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { OauthCallback } from '@/features/auth/components/oauth-callback';

type OauthLoginCallbackRouteProps = {
  params: Promise<{ provider: string }>;
};

export default async function OauthLoginCallbackRoute({ params }: OauthLoginCallbackRouteProps) {
  const { provider } = await params;
  const t = await getTranslations('auth.oauthCallback');
  const common = await getTranslations('common');

  return (
    <AuthShell brand={common('brand')} title={t('title')}>
      <Suspense fallback={<p className="text-sm text-muted-foreground">{t('loading')}</p>}>
        <OauthCallback provider={provider} />
      </Suspense>
    </AuthShell>
  );
}
