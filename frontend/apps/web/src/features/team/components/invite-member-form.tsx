'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { Controller, useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { listProjects } from '@/features/workspace/api/workspace-api';
import { queryKeys } from '@/shared/api-client';
import {
  getErrorMessage,
  isInsufficientPermissions,
} from '@/shared/lib/errors';

import { createInvitation } from '../api/team-api';
import { inviteMemberSchema, inviteRoles, type InviteMemberValues } from '../model/schemas';

type InviteMemberFormProps = {
  workspaceId: string;
};

export function InviteMemberForm({ workspaceId }: InviteMemberFormProps) {
  const t = useTranslations('team');
  const tRoles = useTranslations('roles');
  const tProblems = useTranslations('problems');
  const queryClient = useQueryClient();

  const projectsQuery = useQuery({
    queryKey: queryKeys.projects.list(workspaceId),
    queryFn: () => listProjects(workspaceId),
  });

  const {
    register,
    handleSubmit,
    control,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<InviteMemberValues>({
    resolver: zodResolver(inviteMemberSchema),
    defaultValues: { email: '', role: 'editor', projectIds: [] },
  });

  const selectedProjects = watch('projectIds') ?? [];

  const mutation = useMutation({
    mutationFn: (values: InviteMemberValues) =>
      createInvitation(workspaceId, {
        email: values.email,
        role: values.role,
        projectIds: values.projectIds,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.workspaces.invitations(workspaceId),
      });
      reset({ email: '', role: 'editor', projectIds: [] });
      toast({ title: t('invite.successTitle'), description: t('invite.successBody') });
    },
    onError: (error) => {
      const description = isInsufficientPermissions(error)
        ? tProblems('insufficient_permissions')
        : getErrorMessage(error, t('invite.errorTitle'));
      toast({
        variant: 'destructive',
        title: t('invite.errorTitle'),
        description,
      });
    },
  });

  const toggleProject = (projectId: string, checked: boolean) => {
    const next = checked
      ? [...selectedProjects, projectId]
      : selectedProjects.filter((id) => id !== projectId);
    setValue('projectIds', next, { shouldDirty: true });
  };

  return (
    <form
      className="space-y-4"
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      data-testid="invite-member-form"
    >
      <FormField
        label={t('invite.email')}
        type="email"
        autoComplete="email"
        error={
          errors.email
            ? t(`validation.${errors.email.message as 'emailRequired' | 'emailInvalid'}`)
            : undefined
        }
        {...register('email')}
      />
      <div className="space-y-1.5">
        <label htmlFor="invite-role" className="text-sm font-medium text-foreground">
          {t('invite.role')}
        </label>
        <Controller
          control={control}
          name="role"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="invite-role" aria-label={t('invite.role')} data-testid="invite-role">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {inviteRoles.map((role) => (
                  <SelectItem key={role} value={role}>
                    {tRoles(role)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
        {errors.role ? (
          <p role="alert" className="text-xs text-destructive">
            {t('validation.roleRequired')}
          </p>
        ) : null}
      </div>
      <fieldset className="space-y-2" data-testid="invite-projects">
        <legend className="text-sm font-medium text-foreground">{t('invite.projects')}</legend>
        <p className="text-xs text-muted-foreground">{t('invite.projectsHint')}</p>
        {projectsQuery.isLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : (
          <ul className="space-y-2" role="list">
            {(projectsQuery.data?.items ?? []).map((project) => {
              const id = project.id!;
              const inputId = `invite-project-${id}`;
              return (
                <li key={id} className="flex items-center gap-2">
                  <input
                    id={inputId}
                    type="checkbox"
                    className="h-4 w-4 rounded border-input accent-primary"
                    checked={selectedProjects.includes(id)}
                    onChange={(event) => toggleProject(id, event.target.checked)}
                  />
                  <label htmlFor={inputId} className="flex items-center gap-2 text-sm text-foreground">
                    <span
                      className="inline-block h-2.5 w-2.5 rounded-full"
                      style={{ backgroundColor: project.color ?? '#64748b' }}
                      aria-hidden
                    />
                    {project.name}
                  </label>
                </li>
              );
            })}
            {(projectsQuery.data?.items ?? []).length === 0 ? (
              <li className="text-xs text-muted-foreground">{t('invite.projectsEmpty')}</li>
            ) : null}
          </ul>
        )}
      </fieldset>
      <Button type="submit" disabled={mutation.isPending} data-testid="invite-submit">
        {mutation.isPending ? t('submitting') : t('invite.submit')}
      </Button>
    </form>
  );
}
