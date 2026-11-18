'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Input, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';

import {
  legalRequisitesSchema,
  loadLegalDraft,
  saveLegalDraft,
  type LegalRequisitesValues,
} from '../model/legal-schema';

type LegalRequisitesFormProps = {
  workspaceId: string;
  canManage?: boolean;
};

export function LegalRequisitesForm({ workspaceId, canManage }: LegalRequisitesFormProps) {
  const t = useTranslations('billing.legal');
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<LegalRequisitesValues>({
    resolver: zodResolver(legalRequisitesSchema),
    defaultValues: {
      inn: '',
      kpp: '',
      ogrn: '',
      companyName: '',
      address: '',
    },
  });

  useEffect(() => {
    const draft = loadLegalDraft(workspaceId);
    if (draft) {
      reset(draft);
    }
  }, [workspaceId, reset]);

  if (!canManage) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="billing-legal-readonly">
        {t('viewOnly')}
      </p>
    );
  }

  return (
    <form
      className="space-y-3 rounded-xl border border-border p-4"
      data-testid="billing-legal-form"
      onSubmit={handleSubmit((values) => {
        saveLegalDraft(workspaceId, values);
        toast({ title: t('draftSaved'), description: t('draftHint') });
      })}
    >
      <div>
        <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <label className="space-y-1 text-sm">
          <span>{t('inn')}</span>
          <Input {...register('inn')} data-testid="billing-legal-inn" aria-invalid={Boolean(errors.inn)} />
          {errors.inn ? (
            <span className="text-xs text-destructive" data-testid="billing-legal-inn-error">
              {t(`errors.${errors.inn.message as string}`)}
            </span>
          ) : null}
        </label>
        <label className="space-y-1 text-sm">
          <span>{t('kpp')}</span>
          <Input {...register('kpp')} data-testid="billing-legal-kpp" aria-invalid={Boolean(errors.kpp)} />
          {errors.kpp ? (
            <span className="text-xs text-destructive" data-testid="billing-legal-kpp-error">
              {t(`errors.${errors.kpp.message as string}`)}
            </span>
          ) : null}
        </label>
        <label className="space-y-1 text-sm md:col-span-2">
          <span>{t('ogrn')}</span>
          <Input {...register('ogrn')} data-testid="billing-legal-ogrn" aria-invalid={Boolean(errors.ogrn)} />
          {errors.ogrn ? (
            <span className="text-xs text-destructive">{t(`errors.${errors.ogrn.message as string}`)}</span>
          ) : null}
        </label>
        <label className="space-y-1 text-sm md:col-span-2">
          <span>{t('companyName')}</span>
          <Input
            {...register('companyName')}
            data-testid="billing-legal-company"
            aria-invalid={Boolean(errors.companyName)}
          />
          {errors.companyName ? (
            <span className="text-xs text-destructive">
              {t(`errors.${errors.companyName.message as string}`)}
            </span>
          ) : null}
        </label>
        <label className="space-y-1 text-sm md:col-span-2">
          <span>{t('address')}</span>
          <Input
            {...register('address')}
            data-testid="billing-legal-address"
            aria-invalid={Boolean(errors.address)}
          />
          {errors.address ? (
            <span className="text-xs text-destructive">{t(`errors.${errors.address.message as string}`)}</span>
          ) : null}
        </label>
      </div>

      <Button type="submit" disabled={isSubmitting} data-testid="billing-legal-submit">
        {isSubmitting ? t('pending') : t('submit')}
      </Button>
    </form>
  );
}
