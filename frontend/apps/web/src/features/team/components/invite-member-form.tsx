'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
} from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { Controller, useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
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

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors },
  } = useForm<InviteMemberValues>({
    resolver: zodResolver(inviteMemberSchema),
    defaultValues: { email: '', role: 'editor', projectIds: [] },
  });

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
      <Button type="submit" disabled={mutation.isPending} data-testid="invite-submit">
        {mutation.isPending ? t('submitting') : t('invite.submit')}
      </Button>
    </form>
  );
}
