'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import { deleteWorkspace, getWorkspace, updateWorkspace } from '../api/workspace-api';
import { updateWorkspaceSchema, type UpdateWorkspaceValues } from '../model/schemas';

type WorkspaceSettingsPanelProps = {
  workspaceId: string;
};

export function WorkspaceSettingsPanel({ workspaceId }: WorkspaceSettingsPanelProps) {
  const t = useTranslations('workspace');
  const router = useRouter();
  const queryClient = useQueryClient();
  const canUpdate = useCan('workspace.update', workspaceId);
  const canDelete = useCan('workspace.delete', workspaceId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: queryKeys.workspaces.detail(workspaceId),
    queryFn: () => getWorkspace(workspaceId),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<UpdateWorkspaceValues>({
    resolver: zodResolver(updateWorkspaceSchema),
    defaultValues: { name: '' },
  });

  useEffect(() => {
    if (data?.name) {
      reset({ name: data.name });
    }
  }, [data?.name, reset]);

  const saveMutation = useMutation({
    mutationFn: (values: UpdateWorkspaceValues) =>
      updateWorkspace(workspaceId, { name: values.name }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.detail(workspaceId) });
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.profile() });
      toast({ title: t('settings.savedTitle'), description: t('settings.savedBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('settings.errorTitle'),
        description: getErrorMessage(error, t('settings.errorTitle')),
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteWorkspace(workspaceId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.profile() });
      toast({ title: t('settings.deletedTitle'), description: t('settings.deletedBody') });
      router.push('/app');
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('settings.errorTitle'),
        description: getErrorMessage(error, t('settings.errorTitle')),
      });
    },
  });

  if (isLoading) {
    return <p className="text-sm text-muted-foreground">{t('loading')}</p>;
  }

  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-8" data-testid="workspace-settings">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t('settings.title')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('settings.description')}</p>
      </div>

      {canUpdate ? (
        <form
          className="space-y-4"
          onSubmit={handleSubmit((values) => saveMutation.mutate(values))}
          noValidate
        >
          <FormField
            label={t('fields.name')}
            error={
              errors.name
                ? t(`validation.${errors.name.message as 'nameRequired' | 'nameMax'}`)
                : undefined
            }
            {...register('name')}
          />
          <Button type="submit" disabled={!isDirty || saveMutation.isPending}>
            {saveMutation.isPending ? t('submitting') : t('settings.save')}
          </Button>
        </form>
      ) : (
        <p className="text-sm text-muted-foreground">{t('settings.readOnly')}</p>
      )}

      <div className="space-y-2 border-t border-border pt-6">
        <h2 className="text-lg font-medium">{t('settings.projectsLinkTitle')}</h2>
        <p className="text-sm text-muted-foreground">{t('settings.projectsLinkBody')}</p>
        <Button asChild variant="secondary">
          <Link href={`/app/${workspaceId}/projects`}>{t('settings.projectsLinkCta')}</Link>
        </Button>
      </div>

      {canDelete ? (
        <div className="space-y-3 border-t border-border pt-6">
          <h2 className="text-lg font-medium text-destructive">{t('settings.dangerTitle')}</h2>
          <p className="text-sm text-muted-foreground">{t('settings.dangerBody')}</p>
          {!confirmDelete ? (
            <Button
              type="button"
              variant="destructive"
              onClick={() => setConfirmDelete(true)}
              data-testid="workspace-delete"
            >
              {t('settings.delete')}
            </Button>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="destructive"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate()}
                data-testid="workspace-delete-confirm"
              >
                {t('settings.deleteConfirm')}
              </Button>
              <Button type="button" variant="outline" onClick={() => setConfirmDelete(false)}>
                {t('settings.deleteCancel')}
              </Button>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
