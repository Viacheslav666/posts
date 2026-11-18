'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Skeleton, toast } from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslations } from 'next-intl';

import { FormField } from '@/features/auth/components/form-field';
import { queryKeys } from '@/shared/api-client';
import { useMe } from '@/shared/hooks/use-me';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  changePassword,
  confirmTwoFactor,
  disableTwoFactor,
  listSessions,
  revokeSession,
  setupTwoFactor,
} from '../api/me-settings-api';
import { passwordSchema, type PasswordFormValues } from '../model/schemas';
import { SettingsShell } from './settings-shell';

type SecuritySettingsPanelProps = {
  workspaceId: string;
};

export function SecuritySettingsPanel({ workspaceId }: SecuritySettingsPanelProps) {
  const t = useTranslations('settings.security');
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const [setupSecret, setSetupSecret] = useState<string | null>(null);
  const [totpCode, setTotpCode] = useState('');
  const [disablePassword, setDisablePassword] = useState('');
  const [disableCode, setDisableCode] = useState('');

  const sessionsQuery = useQuery({
    queryKey: queryKeys.me.sessions(),
    queryFn: listSessions,
  });

  const passwordForm = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const passwordMutation = useMutation({
    mutationFn: (values: PasswordFormValues) =>
      changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      }),
    onSuccess: () => {
      passwordForm.reset();
      toast({ title: t('password.savedTitle'), description: t('password.savedBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('password.errorTitle'),
        description: getErrorMessage(error, t('password.errorTitle')),
      });
    },
  });

  const setupMutation = useMutation({
    mutationFn: setupTwoFactor,
    onSuccess: (data) => {
      setSetupSecret(data.secret ?? data.otpauthUrl ?? 'ok');
      toast({ title: t('totp.setupTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('totp.errorTitle'),
        description: getErrorMessage(error, t('totp.errorTitle')),
      });
    },
  });

  const confirmMutation = useMutation({
    mutationFn: () => confirmTwoFactor({ code: totpCode }),
    onSuccess: async () => {
      setSetupSecret(null);
      setTotpCode('');
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.profile() });
      toast({ title: t('totp.enabledTitle'), description: t('totp.enabledBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('totp.errorTitle'),
        description: getErrorMessage(error, t('totp.errorTitle')),
      });
    },
  });

  const disableMutation = useMutation({
    mutationFn: () =>
      disableTwoFactor({ password: disablePassword, totpCode: disableCode }),
    onSuccess: async () => {
      setDisablePassword('');
      setDisableCode('');
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.profile() });
      toast({ title: t('totp.disabledTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('totp.errorTitle'),
        description: getErrorMessage(error, t('totp.errorTitle')),
      });
    },
  });

  const revokeMutation = useMutation({
    mutationFn: (sessionId: string) => revokeSession(sessionId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.sessions() });
      toast({ title: t('sessions.revokedTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('sessions.errorTitle'),
        description: getErrorMessage(error, t('sessions.errorTitle')),
      });
    },
  });

  return (
    <SettingsShell workspaceId={workspaceId} title={t('title')} description={t('description')}>
      <section className="space-y-4" data-testid="security-password">
        <h2 className="text-lg font-medium">{t('password.title')}</h2>
        <form
          className="space-y-3"
          onSubmit={passwordForm.handleSubmit((values) => passwordMutation.mutate(values))}
          noValidate
        >
          <FormField
            label={t('password.current')}
            type="password"
            autoComplete="current-password"
            error={
              passwordForm.formState.errors.currentPassword
                ? t('password.validation.currentRequired')
                : undefined
            }
            {...passwordForm.register('currentPassword')}
          />
          <FormField
            label={t('password.next')}
            type="password"
            autoComplete="new-password"
            error={
              passwordForm.formState.errors.newPassword
                ? t('password.validation.passwordMin')
                : undefined
            }
            {...passwordForm.register('newPassword')}
          />
          <FormField
            label={t('password.confirm')}
            type="password"
            autoComplete="new-password"
            error={
              passwordForm.formState.errors.confirmPassword
                ? t(
                    `password.validation.${passwordForm.formState.errors.confirmPassword.message as 'passwordMin' | 'passwordMismatch'}`,
                  )
                : undefined
            }
            {...passwordForm.register('confirmPassword')}
          />
          <Button type="submit" disabled={passwordMutation.isPending}>
            {passwordMutation.isPending ? t('password.saving') : t('password.save')}
          </Button>
        </form>
      </section>

      <section className="space-y-3 border-t border-border pt-6" data-testid="security-totp">
        <h2 className="text-lg font-medium">{t('totp.title')}</h2>
        <p className="text-sm text-muted-foreground">
          {me?.totpEnabled ? t('totp.enabledHint') : t('totp.disabledHint')}
        </p>
        {!me?.totpEnabled ? (
          <div className="space-y-3">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setupMutation.mutate()}
              disabled={setupMutation.isPending}
            >
              {t('totp.setup')}
            </Button>
            {setupSecret ? (
              <div className="space-y-2 rounded-md border border-border p-3">
                <p className="text-sm text-muted-foreground">{t('totp.secretHint')}</p>
                <code className="block break-all text-xs">{setupSecret}</code>
                <FormField
                  label={t('totp.code')}
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value)}
                />
                <Button
                  type="button"
                  onClick={() => confirmMutation.mutate()}
                  disabled={confirmMutation.isPending || totpCode.length < 6}
                >
                  {t('totp.confirm')}
                </Button>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="space-y-2">
            <FormField
              label={t('password.current')}
              type="password"
              value={disablePassword}
              onChange={(e) => setDisablePassword(e.target.value)}
            />
            <FormField
              label={t('totp.code')}
              value={disableCode}
              onChange={(e) => setDisableCode(e.target.value)}
            />
            <Button
              type="button"
              variant="destructive"
              onClick={() => disableMutation.mutate()}
              disabled={disableMutation.isPending}
            >
              {t('totp.disable')}
            </Button>
          </div>
        )}
      </section>

      <section className="space-y-3 border-t border-border pt-6" data-testid="security-sessions">
        <h2 className="text-lg font-medium">{t('sessions.title')}</h2>
        {sessionsQuery.isLoading ? (
          <Skeleton className="h-20 w-full" />
        ) : (
          <ul className="space-y-2">
            {(sessionsQuery.data ?? []).map((session) => (
              <li
                key={session.id}
                className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border px-3 py-2"
              >
                <div className="min-w-0 text-sm">
                  <p className="font-medium text-foreground">
                    {session.current ? t('sessions.current') : session.ip}
                  </p>
                  <p className="truncate text-muted-foreground">{session.userAgent}</p>
                </div>
                {!session.current && session.id ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => revokeMutation.mutate(session.id!)}
                    disabled={revokeMutation.isPending}
                  >
                    {t('sessions.revoke')}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </section>
    </SettingsShell>
  );
}
