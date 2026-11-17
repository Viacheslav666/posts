'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, RoleBadge, Skeleton, toast, type WorkspaceRole } from '@posts/ui';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { getAccessToken } from '@/shared/api-client';
import { getErrorMessage, isApiProblemError } from '@/shared/lib/errors';
import { isWorkspaceRole } from '@/shared/lib/permissions';

import { acceptInvitation, previewInvitation } from '../api/team-api';
import { acceptInvitationSchema, type AcceptInvitationValues } from '../model/schemas';

type AcceptInvitationFormProps = {
  initialToken?: string;
};

export function AcceptInvitationForm({ initialToken = '' }: AcceptInvitationFormProps) {
  const t = useTranslations('invite');
  const tRoles = useTranslations('roles');
  const router = useRouter();
  const loggedIn = Boolean(getAccessToken());

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<AcceptInvitationValues>({
    resolver: zodResolver(acceptInvitationSchema),
    defaultValues: { token: initialToken },
  });

  useEffect(() => {
    if (initialToken) {
      setValue('token', initialToken);
    }
  }, [initialToken, setValue]);

  const token = watch('token');

  const preview = useQuery({
    queryKey: ['invitation-preview', token],
    queryFn: () => previewInvitation(token),
    enabled: Boolean(token?.trim()),
    retry: false,
  });

  const mutation = useMutation({
    mutationFn: (values: AcceptInvitationValues) => acceptInvitation({ token: values.token }),
    onSuccess: (workspace) => {
      toast({ title: t('successTitle'), description: t('successBody') });
      if (workspace.id) {
        router.push(`/app/${workspace.id}/team`);
      }
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error, t('errorTitle')),
      });
    },
  });

  const returnUrl = `/invite?token=${encodeURIComponent(token || initialToken)}`;
  const role = preview.data?.role;
  const roleTyped: WorkspaceRole | null = isWorkspaceRole(role) ? role : null;

  return (
    <div className="space-y-6" data-testid="accept-invitation">
      {preview.isLoading ? <Skeleton className="h-16 w-full" /> : null}

      {preview.data ? (
        <div
          className="space-y-2 rounded-md border border-border bg-muted/40 p-4"
          data-testid="invite-preview"
        >
          <p className="text-sm text-foreground">
            <span className="text-muted-foreground">{t('previewEmail')}: </span>
            {preview.data.email}
          </p>
          {roleTyped ? (
            <p className="flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">{t('previewRole')}:</span>
              <RoleBadge role={roleTyped} label={tRoles(roleTyped)} />
            </p>
          ) : null}
        </div>
      ) : null}

      {preview.isError && isApiProblemError(preview.error) ? (
        <p role="alert" className="text-sm text-destructive">
          {t('previewError')}
        </p>
      ) : null}

      {!loggedIn ? (
        <div className="space-y-3 rounded-md border border-border p-4">
          <p className="text-sm text-muted-foreground">{t('loginRequired')}</p>
          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link href={`/login?returnUrl=${encodeURIComponent(returnUrl)}`}>{t('toLogin')}</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href={`/register?returnUrl=${encodeURIComponent(returnUrl)}`}>
                {t('toRegister')}
              </Link>
            </Button>
          </div>
        </div>
      ) : (
        <form
          className="space-y-4"
          onSubmit={handleSubmit((values) => mutation.mutate(values))}
          noValidate
          data-testid="accept-invitation-form"
        >
          <FormField
            label={t('fields.token')}
            error={errors.token ? t('validation.tokenRequired') : undefined}
            {...register('token')}
          />
          <Button type="submit" disabled={mutation.isPending || !token} data-testid="accept-submit">
            {mutation.isPending ? t('submitting') : t('submit')}
          </Button>
        </form>
      )}
    </div>
  );
}
