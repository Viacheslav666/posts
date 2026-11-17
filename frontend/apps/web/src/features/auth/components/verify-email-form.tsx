'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { verifyEmailRequest } from '../api/auth-api';
import { verifyEmailSchema, type VerifyEmailValues } from '../model/schemas';
import { FormField } from './form-field';

type VerifyEmailFormProps = {
  email: string;
};

export function VerifyEmailForm({ email }: VerifyEmailFormProps) {
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
    defaultValues: { code: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      await verifyEmailRequest({ email, code: values.code });
      toast({ title: t('verify.successTitle'), description: t('verify.successBody') });
      router.push('/login');
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('verify.errorTitle'),
        description: error instanceof Error ? error.message : t('genericError'),
      });
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate>
      <FormField
        label={t('fields.code')}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        hint={t('verify.codeHint')}
        error={errors.code ? tErrors(errors.code.message as 'codeRequired') : undefined}
        {...register('code')}
      />
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? t('submitting') : t('verify.submit')}
      </Button>
    </form>
  );
}
