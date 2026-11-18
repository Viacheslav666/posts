'use client';

import {
  Badge,
  Button,
  EmptyState,
  Input,
  Skeleton,
  toast,
  ConfirmActionDialog,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import {
  useAdminUser,
  useImpersonateMutation,
  useIsPlatformAdmin,
} from '../hooks/use-admin-queries';

type AdminUserDetailPageProps = {
  userId: string;
};

export function AdminUserDetailPage({ userId }: AdminUserDetailPageProps) {
  const t = useTranslations('admin.users');
  const tImp = useTranslations('admin.impersonation');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminUser(userId, isPlatformAdmin);
  const impersonate = useImpersonateMutation();
  const router = useRouter();
  const [reason, setReason] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);

  if (query.isLoading) {
    return <Skeleton className="h-48 w-full" data-testid="admin-user-loading" />;
  }

  if (query.isError || !query.data) {
    return (
      <EmptyState title={t('detailErrorTitle')} description={t('detailErrorBody')} data-testid="admin-user-error" />
    );
  }

  const user = query.data;

  return (
    <div className="space-y-6" data-testid="admin-user-detail">
      <div>
        <Link href="/admin/users" className="text-sm text-muted-foreground hover:text-foreground">
          ← {t('back')}
        </Link>
        <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight">{user.email}</h1>
        <p className="text-sm text-muted-foreground">{user.name}</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {user.platformAdmin ? <Badge>{t('platformAdmin')}</Badge> : null}
          {user.emailVerified ? <Badge variant="secondary">{t('verified')}</Badge> : null}
          {user.totpEnabled ? <Badge variant="secondary">{t('totp')}</Badge> : null}
        </div>
      </div>

      <section className="space-y-2 rounded-xl border border-border bg-card/50 p-4">
        <h2 className="font-semibold">{t('workspacesTitle')}</h2>
        {!user.workspaces?.length ? (
          <p className="text-sm text-muted-foreground">{t('noWorkspaces')}</p>
        ) : (
          <ul className="space-y-1" role="list">
            {user.workspaces.map((ws) => (
              <li key={ws.workspaceId}>
                <Link
                  href={`/admin/workspaces/${ws.workspaceId}`}
                  className="text-primary underline-offset-2 hover:underline"
                >
                  {ws.workspaceName}
                </Link>
                <span className="ml-2 text-xs text-muted-foreground">
                  {ws.role} · {ws.billingStatus}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section
        className="space-y-3 rounded-xl border border-amber-500/40 bg-amber-500/5 p-4"
        data-testid="admin-impersonate-panel"
      >
        <h2 className="font-semibold">{tImp('startTitle')}</h2>
        <p className="text-sm text-muted-foreground">{tImp('startBody')}</p>
        <label className="block space-y-1 text-sm">
          <span>{tImp('reasonLabel')}</span>
          <Input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={tImp('reasonPlaceholder')}
            data-testid="admin-impersonate-reason"
          />
        </label>
        <Button
          type="button"
          disabled={reason.trim().length < 3 || impersonate.isPending}
          onClick={() => setConfirmOpen(true)}
          data-testid="admin-impersonate-open"
        >
          {tImp('start')}
        </Button>
      </section>

      <ConfirmActionDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={tImp('confirmTitle')}
        description={tImp('confirmBody', { email: user.email ?? '' })}
        confirmLabel={tImp('confirm')}
        cancelLabel={tImp('cancel')}
        destructive={false}
        busy={impersonate.isPending}
        testId="admin-impersonate-dialog"
        confirmTestId="admin-impersonate-confirm"
        onConfirm={() => {
          impersonate.mutate(
            { userId, reason: reason.trim() },
            {
              onSuccess: () => {
                setConfirmOpen(false);
                toast({ title: tImp('started') });
                const ws = user.workspaces?.[0]?.workspaceId;
                router.push(ws ? `/app/${ws}` : '/');
              },
              onError: (error) => {
                toast({
                  variant: 'destructive',
                  title: tImp('errorTitle'),
                  description: getErrorMessage(error),
                });
              },
            },
          );
        }}
      />
    </div>
  );
}
