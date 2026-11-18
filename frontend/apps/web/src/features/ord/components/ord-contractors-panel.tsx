'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import type { OrdContractor } from '@posts/api-client';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  Skeleton,
  toast,
} from '@posts/ui';
import { Users } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';
import { isValidInn } from '@/shared/lib/inn';

import {
  useCreateOrdContractor,
  useOrdContractors,
  useOrdOksm,
  useUpdateOrdContractor,
} from '../hooks/use-ord-queries';
import { ordContractorSchema, type OrdContractorFormValues } from '../model/schemas';

type OrdContractorsPanelProps = {
  workspaceId: string;
};

const defaultContractorValues: OrdContractorFormValues = {
  inn: '',
  name: '',
  orgType: 'juridical',
  kpp: '',
  phone: '',
  address: '',
  countryOksm: '643',
};

export function OrdContractorsPanel({ workspaceId }: OrdContractorsPanelProps) {
  const t = useTranslations('ord');
  const canManage = useCan('ord.contractors.manage', workspaceId);
  const contractorsQuery = useOrdContractors(workspaceId);
  useOrdOksm();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<OrdContractor | null>(null);
  const [innClientError, setInnClientError] = useState<string | undefined>();

  const createMutation = useCreateOrdContractor(workspaceId);
  const updateMutation = useUpdateOrdContractor(workspaceId);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<OrdContractorFormValues>({
    resolver: zodResolver(ordContractorSchema),
    defaultValues: defaultContractorValues,
  });

  const openCreate = () => {
    setEditing(null);
    setInnClientError(undefined);
    reset(defaultContractorValues);
    setDialogOpen(true);
  };

  const openEdit = (contractor: OrdContractor) => {
    setEditing(contractor);
    setInnClientError(undefined);
    reset({
      inn: contractor.inn ?? '',
      name: contractor.name ?? '',
      orgType: contractor.orgType ?? 'juridical',
      kpp: contractor.kpp ?? '',
      phone: contractor.phone ?? '',
      address: contractor.address ?? '',
      countryOksm: contractor.countryOksm ?? '643',
    });
    setDialogOpen(true);
  };

  const onSubmit = handleSubmit(async (values) => {
    setInnClientError(undefined);
    if (!isValidInn(values.inn)) {
      setInnClientError(t('errors.ad_inn_invalid'));
      return;
    }

    try {
      if (editing?.id) {
        await updateMutation.mutateAsync({
          contractorId: editing.id,
          body: {
            inn: values.inn,
            name: values.name,
            orgType: values.orgType,
            kpp: values.kpp || undefined,
            phone: values.phone || undefined,
            address: values.address || undefined,
            countryOksm: values.countryOksm || undefined,
          },
        });
        toast({ title: t('contractors.updatedTitle') });
      } else {
        await createMutation.mutateAsync({
          inn: values.inn,
          name: values.name,
          orgType: values.orgType,
          kpp: values.kpp || undefined,
          phone: values.phone || undefined,
          address: values.address || undefined,
          countryOksm: values.countryOksm || undefined,
        });
        toast({ title: t('contractors.createdTitle') });
      }
      setDialogOpen(false);
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('contractors.errorTitle'),
        description: getErrorMessage(error, t('contractors.errorTitle')),
      });
    }
  });

  const innError =
    innClientError ??
    (errors.inn
      ? errors.inn.message === 'ad_inn_invalid'
        ? t('errors.ad_inn_invalid')
        : t('validation.innRequired')
      : undefined);

  return (
    <div className="space-y-4" data-testid="ord-contractors">
      {canManage ? (
        <Button type="button" onClick={openCreate}>
          {t('contractors.add')}
        </Button>
      ) : null}

      {contractorsQuery.isLoading ? (
        <Skeleton className="h-24 w-full" />
      ) : (contractorsQuery.data?.items?.length ?? 0) === 0 ? (
        <EmptyState
          icon={<Users aria-hidden />}
          title={t('contractors.emptyTitle')}
          description={t('contractors.emptyBody')}
        />
      ) : (
        <ul className="divide-y divide-border rounded-md border border-border">
          {contractorsQuery.data?.items?.map((contractor) => (
            <li
              key={contractor.id}
              className="flex flex-wrap items-center justify-between gap-2 px-4 py-3"
            >
              <div>
                <p className="font-medium text-foreground">{contractor.name}</p>
                <p className="text-sm text-muted-foreground">
                  {t('contractors.innLabel')}: {contractor.inn}
                </p>
              </div>
              {canManage ? (
                <Button type="button" variant="outline" size="sm" onClick={() => openEdit(contractor)}>
                  {t('contractors.edit')}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editing ? t('contractors.editTitle') : t('contractors.createTitle')}
            </DialogTitle>
            <DialogDescription>{t('contractors.dialogDescription')}</DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <FormField
              label={t('contractors.inn')}
              inputMode="numeric"
              data-testid="ord-contractor-inn"
              error={innError}
              {...register('inn')}
            />
            <FormField
              label={t('contractors.name')}
              error={errors.name ? t('validation.nameRequired') : undefined}
              {...register('name')}
            />
            <FormField
              label={t('contractors.orgType')}
              error={errors.orgType ? t('validation.orgTypeRequired') : undefined}
              {...register('orgType')}
            />
            <FormField label={t('contractors.kpp')} {...register('kpp')} />
            <FormField label={t('contractors.phone')} type="tel" {...register('phone')} />
            <FormField label={t('contractors.address')} {...register('address')} />
            <DialogFooter className="gap-2 sm:gap-0">
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                {t('contractors.cancel')}
              </Button>
              <Button
                type="submit"
                disabled={
                  isSubmitting || createMutation.isPending || updateMutation.isPending
                }
              >
                {t('contractors.save')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}