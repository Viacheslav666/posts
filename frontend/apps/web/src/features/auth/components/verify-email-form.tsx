'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { getErrorMessage } from '@/shared/lib/errors';

import { verifyEmailRequest } from '../api/auth-api';
import { verifyEmailSchema, type VerifyEmailValues } from '../model/schemas';
import { FormField } from './form-field';

type VerifyEmailFormProps = {
  /** Pre-filled token from email link query (`?token=`). */
  initialToken?: string;
};

export function VerifyEmailForm({ initialToken = '' }: VerifyEmailFormProps) {
  const t = useTranslations('auth');
  const tErrors = useTranslations('auth.validation');
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<VerifyEmailValues>({
    resolver: zodResolver(verifyEmailSchema),
    defaultValues: { token: initialToken },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      await verifyEmailRequest({ token: values.token });
      toast({ title: t('verify.successTitle'), description: t('verify.successBody') });
      router.push('/login');
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('verify.errorTitle'),
        description: getErrorMessage(error, t('genericError')),
      });
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate>
      <FormField
        label={t('fields.code')}
        autoComplete="one-time-code"
        hint={t('verify.codeHint')}
        error={errors.token ? tErrors(errors.token.message as 'codeRequired') : undefined}
        {...register('token')}
      />
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? t('submitting') : t('verify.submit')}
      </Button>
    </form>
  );
}
