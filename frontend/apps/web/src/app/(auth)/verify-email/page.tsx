import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { VerifyEmailForm } from '@/features/auth/components/verify-email-form';

type VerifyEmailPageProps = {
  searchParams: Promise<{ email?: string }>;
};

export default async function VerifyEmailPage({ searchParams }: VerifyEmailPageProps) {
  const t = await getTranslations('auth');
  const common = await getTranslations('common');
  const params = await searchParams;
  const email = params.email?.trim() || 'demo@posts.app';

  return (
    <AuthShell
      brand={common('brand')}
      title={t('verify.title')}
      description={t('verify.description', { email })}
      footer={
        <p>
          <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            {t('verify.toLogin')}
          </Link>
        </p>
      }
    >
      <VerifyEmailForm email={email} />
    </AuthShell>
  );
}
