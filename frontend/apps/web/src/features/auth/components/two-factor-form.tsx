'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { readPendingLogin } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { completeTwoFactorLogin } from '../api/auth-api';
import { twoFactorSchema, type TwoFactorValues } from '../model/schemas';
import { FormField } from './form-field';

export function TwoFactorForm() {
  const t = useTranslations('auth');
  const tErrors = useTranslations('auth.validation');
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<TwoFactorValues>({
    resolver: zodResolver(twoFactorSchema),
    defaultValues: { code: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      const pending = readPendingLogin();
      if (!pending) {
        toast({
          variant: 'destructive',
          title: t('twoFactor.errorTitle'),
          description: t('twoFactor.missingPending'),
        });
        router.push('/login');
        return;
      }
      await completeTwoFactorLogin({
        email: pending.email,
        password: pending.password,
        totpCode: values.code,
      });
      toast({ title: t('twoFactor.successTitle'), description: t('twoFactor.successBody') });
      router.push('/app/demo');
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('twoFactor.errorTitle'),
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
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        hint={t('twoFactor.codeHint')}
        error={errors.code ? tErrors(errors.code.message as 'codeRequired') : undefined}
        {...register('code')}
      />
      <Button type="submit" className="w-full" disabled={submitting}>
        {submitting ? t('submitting') : t('twoFactor.submit')}
      </Button>
    </form>
  );
}
