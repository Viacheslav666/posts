'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, toast } from '@posts/ui';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { queryKeys } from '@/shared/api-client';
import {
  getErrorMessage,
  isInsufficientPermissions,
} from '@/shared/lib/errors';

import { createWorkspace } from '../api/workspace-api';
import { createWorkspaceSchema, type CreateWorkspaceValues } from '../model/schemas';

type CreateWorkspaceFormProps = {
  onSuccess?: (workspaceId: string) => void;
};

export function CreateWorkspaceForm({ onSuccess }: CreateWorkspaceFormProps) {
  const t = useTranslations('workspace');
  const tProblems = useTranslations('problems');
  const router = useRouter();
  const queryClient = useQueryClient();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
    reset,
  } = useForm<CreateWorkspaceValues>({
    resolver: zodResolver(createWorkspaceSchema),
    defaultValues: { name: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setSubmitting(true);
    try {
      const workspace = await createWorkspace({ name: values.name });
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.profile() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.workspaces.all() });
      toast({ title: t('create.successTitle'), description: t('create.successBody') });
      reset();
      if (workspace.id) {
        onSuccess?.(workspace.id);
        router.push(`/app/${workspace.id}`);
      }
    } catch (error) {
      const description = isInsufficientPermissions(error)
        ? tProblems('insufficient_permissions')
        : getErrorMessage(error, t('create.errorTitle'));
      toast({
        variant: 'destructive',
        title: t('create.errorTitle'),
        description,
      });
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <form className="space-y-4" onSubmit={onSubmit} noValidate data-testid="create-workspace-form">
      <FormField
        label={t('fields.name')}
        autoComplete="organization"
        error={
          errors.name
            ? t(`validation.${errors.name.message as 'nameRequired' | 'nameMax'}`)
            : undefined
        }
        {...register('name')}
      />
      <Button type="submit" className="w-full" disabled={submitting} data-testid="create-workspace-submit">
        {submitting ? t('submitting') : t('create.submit')}
      </Button>
    </form>
  );
}
