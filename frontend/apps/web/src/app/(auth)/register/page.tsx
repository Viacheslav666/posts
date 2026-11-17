import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { RegisterForm } from '@/features/auth/components/register-form';

export default async function RegisterPage() {
  const t = await getTranslations('auth');
  const common = await getTranslations('common');

  return (
    <AuthShell
      brand={common('brand')}
      title={t('register.title')}
      description={t('register.description')}
      footer={
        <p>
          {t('register.hasAccount')}{' '}
          <Link href="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            {t('register.toLogin')}
          </Link>
        </p>
      }
    >
      <RegisterForm />
    </AuthShell>
  );
}
