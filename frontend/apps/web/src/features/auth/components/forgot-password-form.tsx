'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { getErrorMessage } from '@/shared/lib/errors';

import { forgotPasswordRequest } from '../api/auth-api';
import { forgotPasswordSchema, type ForgotPasswordValues } from '../model/schemas';
import { FormField } from './form-field';

export function ForgotPasswordForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('auth.validation');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      await forgotPasswordRequest(values);
      setSent(true);
      toast({ title: t('forgot.successTitle'), description: t('forgot.successBody') });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('forgot.errorTitle'),
        description: getErrorMessage(error, t('genericError')),
      });
    } finally {
      setSubmitting(false);
    }
  });

  if (sent) {
    return <p className="text-sm text-muted-foreground">{t('forgot.sentBody')}</p>;
  }

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate>
      <FormField
        label={t('fields.email')}
        type="email"
        autoComplete="email"
        error={errors.email ? tErrors(errors.email.message as 'emailRequired') : undefined}
        {...register('email')}
      />
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? t('submitting') : t('forgot.submit')}
      </Button>
    </form>
  );
}
