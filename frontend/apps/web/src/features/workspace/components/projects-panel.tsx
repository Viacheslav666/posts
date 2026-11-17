'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, EmptyState, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FolderKanban } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import { createProject, deleteProject, listProjects, updateProject } from '../api/workspace-api';
import { createProjectSchema, type CreateProjectValues } from '../model/schemas';

type ProjectsPanelProps = {
  workspaceId: string;
};

export function ProjectsPanel({ workspaceId }: ProjectsPanelProps) {
  const t = useTranslations('projects');
  const queryClient = useQueryClient();
  const canManage = useCan('projects.manage', workspaceId);
  const canView = useCan('projects.view', workspaceId);
  const [editingId, setEditingId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
    enabled: canView,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateProjectValues>({
    resolver: zodResolver(createProjectSchema),
    defaultValues: { name: '', color: '#3B82F6' },
  });

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.projects.all(workspaceId) });
  };

  const createMutation = useMutation({
    mutationFn: (values: CreateProjectValues) =>
      createProject(workspaceId, {
        name: values.name,
        color: values.color || undefined,
      }),
    onSuccess: async () => {
      await invalidate();
      reset({ name: '', color: '#3B82F6' });
      toast({ title: t('create.successTitle'), description: t('create.successBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('create.errorTitle'),
        description: getErrorMessage(error, t('create.errorTitle')),
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({
      projectId,
      values,
    }: {
      projectId: string;
      values: CreateProjectValues;
    }) =>
      updateProject(projectId, {
        name: values.name,
        color: values.color || undefined,
      }),
    onSuccess: async () => {
      await invalidate();
      setEditingId(null);
      toast({ title: t('edit.successTitle'), description: t('edit.successBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('edit.errorTitle'),
        description: getErrorMessage(error, t('edit.errorTitle')),
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (projectId: string) => deleteProject(projectId),
    onSuccess: async () => {
      await invalidate();
      toast({ title: t('delete.successTitle'), description: t('delete.successBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('delete.errorTitle'),
        description: getErrorMessage(error, t('delete.errorTitle')),
      });
    },
  });

  if (!canView) {
    return (
      <EmptyState
        icon={<FolderKanban aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6" data-testid="projects-panel">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t('title')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {canManage ? (
        <form
          className="grid gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end"
          onSubmit={handleSubmit((values) => createMutation.mutate(values))}
          noValidate
          data-testid="create-project-form"
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
          <FormField
            label={t('fields.color')}
            type="text"
            placeholder="#3B82F6"
            error={
              errors.color ? t(`validation.${errors.color.message as 'colorInvalid'}`) : undefined
            }
            {...register('color')}
          />
          <Button type="submit" disabled={createMutation.isPending} data-testid="create-project-submit">
            {createMutation.isPending ? t('submitting') : t('create.submit')}
          </Button>
        </form>
      ) : null}

      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : (
        <ul className="divide-y divide-border rounded-md border border-border" role="list">
          {(data?.items ?? []).map((project) => (
            <li
              key={project.id}
              className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              data-testid={`project-row-${project.id}`}
            >
              {editingId === project.id && canManage ? (
                <form
                  className="flex w-full flex-wrap items-end gap-2"
                  onSubmit={(event) => {
                    event.preventDefault();
                    const form = event.currentTarget;
                    const name = (form.elements.namedItem('name') as HTMLInputElement).value;
                    const color = (form.elements.namedItem('color') as HTMLInputElement).value;
                    updateMutation.mutate({
                      projectId: project.id!,
                      values: { name, color },
                    });
                  }}
                >
                  <FormField label={t('fields.name')} name="name" defaultValue={project.name} />
                  <FormField
                    label={t('fields.color')}
                    name="color"
                    defaultValue={project.color ?? ''}
                  />
                  <Button type="submit" size="sm">
                    {t('edit.save')}
                  </Button>
                  <Button type="button" size="sm" variant="ghost" onClick={() => setEditingId(null)}>
                    {t('edit.cancel')}
                  </Button>
                </form>
              ) : (
                <>
                  <div className="flex items-center gap-3">
                    <span
                      className="h-4 w-4 rounded-full border border-border"
                      style={{ backgroundColor: project.color ?? '#94a3b8' }}
                      aria-hidden
                    />
                    <span className="font-medium text-foreground">{project.name}</span>
                  </div>
                  {canManage ? (
                    <div className="flex gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        onClick={() => setEditingId(project.id!)}
                      >
                        {t('edit.action')}
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="destructive"
                        onClick={() => deleteMutation.mutate(project.id!)}
                      >
                        {t('delete.action')}
                      </Button>
                    </div>
                  ) : null}
                </>
              )}
            </li>
          ))}
          {(data?.items ?? []).length === 0 ? (
            <li className="px-4 py-8 text-center text-sm text-muted-foreground">{t('empty')}</li>
          ) : null}
        </ul>
      )}
    </div>
  );
}
