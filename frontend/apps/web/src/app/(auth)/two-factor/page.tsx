import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { TwoFactorForm } from '@/features/auth/components/two-factor-form';

type TwoFactorPageProps = {
  searchParams: Promise<{ challengeId?: string }>;
};

export default async function TwoFactorPage({ searchParams }: TwoFactorPageProps) {
  const t = await getTranslations('auth');
  const common = await getTranslations('common');
  const params = await searchParams;
  const challengeId = params.challengeId?.trim() || 'chal_demo_2fa';

  return (
    <AuthShell
      brand={common('brand')}
      title={t('twoFactor.title')}
      description={t('twoFactor.description')}
      footer={
        <p>
          <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            {t('twoFactor.toLogin')}
          </Link>
        </p>
      }
    >
      <TwoFactorForm challengeId={challengeId} />
    </AuthShell>
  );
}
