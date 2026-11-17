'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { registerRequest } from '../api/auth-api';
import { registerSchema, type RegisterValues } from '../model/schemas';
import { AuthDivider } from './auth-divider';
import { FormField } from './form-field';
import { OauthButtons } from './oauth-buttons';

export function RegisterForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('auth.validation');
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
      acceptTerms: false,
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      const result = await registerRequest({
        name: values.name,
        email: values.email,
        password: values.password,
      });
      toast({
        title: t('register.successTitle'),
        description: t('register.successBody'),
      });
      router.push(`/verify-email?email=${encodeURIComponent(result.email)}`);
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('register.errorTitle'),
        description: error instanceof Error ? error.message : t('genericError'),
      });
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <div>
      <OauthButtons />
      <AuthDivider />
      <form className="space-y-4" onSubmit={onSubmit} noValidate data-testid="register-form">
        <FormField
          label={t('fields.name')}
          autoComplete="name"
          error={errors.name ? tErrors(errors.name.message as 'nameRequired') : undefined}
          {...register('name')}
        />
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
          autoComplete="new-password"
          hint={t('fields.passwordHint')}
          error={errors.password ? tErrors(errors.password.message as 'passwordMin') : undefined}
          {...register('password')}
        />
        <FormField
          label={t('fields.confirmPassword')}
          type="password"
          autoComplete="new-password"
          error={
            errors.confirmPassword
              ? tErrors(errors.confirmPassword.message as 'passwordMismatch')
              : undefined
          }
          {...register('confirmPassword')}
        />
        <div className="space-y-1.5">
          <label className="flex items-start gap-2 text-sm text-foreground">
            <input
              type="checkbox"
              className="mt-1 size-4 rounded border-input text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              {...register('acceptTerms')}
            />
            <span>{t('register.acceptTerms')}</span>
          </label>
          {errors.acceptTerms ? (
            <p role="alert" className="text-xs text-destructive">
              {tErrors(errors.acceptTerms.message as 'termsRequired')}
            </p>
          ) : null}
        </div>
        <Button type="submit" className="w-full" disabled={submitting} data-testid="register-submit">
          {submitting ? t('submitting') : t('register.submit')}
        </Button>
      </form>
    </div>
  );
}
