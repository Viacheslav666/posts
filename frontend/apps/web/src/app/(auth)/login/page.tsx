import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { LoginForm } from '@/features/auth/components/login-form';

export default async function LoginPage() {
  const t = await getTranslations('auth');
  const common = await getTranslations('common');

  return (
    <AuthShell
      brand={common('brand')}
      title={t('login.title')}
      description={t('login.description')}
      footer={
        <p>
          {t('login.noAccount')}{' '}
          <Link href="/register" className="font-medium text-primary underline-offset-4 hover:underline">
            {t('login.toRegister')}
          </Link>
        </p>
      }
    >
      <LoginForm />
    </AuthShell>
  );
}
