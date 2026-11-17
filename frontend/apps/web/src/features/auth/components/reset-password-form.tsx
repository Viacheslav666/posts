'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { resetPasswordRequest } from '../api/auth-api';
import { resetPasswordSchema, type ResetPasswordValues } from '../model/schemas';
import { FormField } from './form-field';

type ResetPasswordFormProps = {
  token: string;
};

export function ResetPasswordForm({ token }: ResetPasswordFormProps) {
  const t = useTranslations('auth');
  const tErrors = useTranslations('auth.validation');
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      await resetPasswordRequest({ token, password: values.password });
      toast({ title: t('reset.successTitle'), description: t('reset.successBody') });
      router.push('/login');
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('reset.errorTitle'),
        description: error instanceof Error ? error.message : t('genericError'),
      });
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate>
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
      <Button type="submit" className="w-full" disabled={submitting || !token}>
        {submitting ? t('submitting') : t('reset.submit')}
      </Button>
    </form>
  );
}
