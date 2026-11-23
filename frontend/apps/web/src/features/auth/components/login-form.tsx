'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { getErrorMessage } from '@/shared/lib/errors';

import { fetchMe, loginRequest } from '../api/auth-api';
import { appHomePath } from '../lib/app-home-path';
import { loginSchema, type LoginValues } from '../model/schemas';
import { AuthDivider } from './auth-divider';
import { FormField } from './form-field';
import { OauthButtons } from './oauth-buttons';

export function LoginForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('auth.validation');
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      const result = await loginRequest(values);
      if (result.status === 'two_factor_required') {
        router.push('/two-factor');
        return;
      }
      toast({ title: t('login.successTitle'), description: t('login.successBody') });
      const me = await fetchMe();
      router.push(appHomePath(me));
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('login.errorTitle'),
        description: getErrorMessage(error, t('genericError')),
      });
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <div>
      <OauthButtons />
      <AuthDivider />
      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <FormField
          label={t('fields.email')}
          type="email"
          autoComplete="email"
          error={errors.email ? tErrors(errors.email.message as 'emailRequired') : undefined}
          {...register('email')}
        />
        <FormField
          label={t('fields.password')}
          type="password"
          autoComplete="current-password"
          error={errors.password ? tErrors(errors.password.message as 'passwordRequired') : undefined}
          {...register('password')}
        />
        <div className="flex justify-end">
          <Link href="/forgot-password" className="text-sm font-medium text-primary underline-offset-4 hover:underline">
            {t('login.forgot')}
          </Link>
        </div>
        <Button type="submit" className="w-full" disabled={submitting} data-testid="login-submit">
          {submitting ? t('submitting') : t('login.submit')}
        </Button>
      </form>
    </div>
  );
}
