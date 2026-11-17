import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { ResetPasswordForm } from '@/features/auth/components/reset-password-form';

type ResetPasswordPageProps = {
  searchParams: Promise<{ token?: string }>;
};

export default async function ResetPasswordPage({ searchParams }: ResetPasswordPageProps) {
  const t = await getTranslations('auth');
  const common = await getTranslations('common');
  const params = await searchParams;
  const token = params.token?.trim() || '';

  return (
    <AuthShell
      brand={common('brand')}
      title={t('reset.title')}
      description={token ? t('reset.description') : t('reset.missingToken')}
      footer={
        <p>
          <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            {t('reset.toLogin')}
          </Link>
        </p>
      }
    >
      <ResetPasswordForm token={token || 'demo-reset-token'} />
    </AuthShell>
  );
}
