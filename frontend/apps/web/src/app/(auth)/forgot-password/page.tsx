import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { ForgotPasswordForm } from '@/features/auth/components/forgot-password-form';

export default async function ForgotPasswordPage() {
  const t = await getTranslations('auth');
  const common = await getTranslations('common');

  return (
    <AuthShell
      brand={common('brand')}
      title={t('forgot.title')}
      description={t('forgot.description')}
      footer={
        <p>
          <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            {t('forgot.toLogin')}
          </Link>
        </p>
      }
    >
      <ForgotPasswordForm />
    </AuthShell>
  );
}
