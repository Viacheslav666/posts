'use client';

import {
  Badge,
  Button,
  ConfirmActionDialog,
  EmptyState,
  Skeleton,
  toast,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import {
  useAdminFeatureFlags,
  useIsPlatformAdmin,
  useUpsertFeatureFlagMutation,
} from '../hooks/use-admin-queries';

export function AdminFeatureFlagsPage() {
  const t = useTranslations('admin.featureFlags');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminFeatureFlags(isPlatformAdmin);
  const upsert = useUpsertFeatureFlagMutation();
  const [pendingDisable, setPendingDisable] = useState<{ key: string; description?: string } | null>(
    null,
  );

  return (
    <div className="space-y-4" data-testid="admin-feature-flags-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {query.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.length ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <ul className="space-y-2" role="list" data-testid="admin-feature-flags-list">
          {query.data.map((flag) => (
            <li
              key={flag.id ?? flag.key}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border px-4 py-3"
            >
              <div>
                <p className="font-mono text-sm font-medium">{flag.key}</p>
                <p className="text-xs text-muted-foreground">{flag.description}</p>
              </div>
              <div className="flex items-center gap-2">
                {flag.enabledGlobal ? (
                  <Badge variant="secondary">{t('enabled')}</Badge>
                ) : (
                  <Badge variant="outline">{t('disabled')}</Badge>
                )}
                <Button
                  size="sm"
                  variant={flag.enabledGlobal ? 'destructive' : 'default'}
                  type="button"
                  data-testid={`admin-flag-toggle-${flag.key}`}
                  onClick={() => {
                    if (flag.enabledGlobal) {
                      setPendingDisable({ key: flag.key!, description: flag.description });
                      return;
                    }
                    upsert.mutate(
                      {
                        key: flag.key!,
                        description: flag.description,
                        enabledGlobal: true,
                      },
                      {
                        onSuccess: () => toast({ title: t('updated') }),
                        onError: (error) =>
                          toast({
                            variant: 'destructive',
                            title: t('errorTitle'),
                            description: getErrorMessage(error),
                          }),
                      },
                    );
                  }}
                >
                  {flag.enabledGlobal ? t('disable') : t('enable')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <ConfirmActionDialog
        open={Boolean(pendingDisable)}
        onOpenChange={(open) => !open && setPendingDisable(null)}
        title={t('confirmDisableTitle')}
        description={t('confirmDisableBody', { key: pendingDisable?.key ?? '' })}
        confirmLabel={t('disable')}
        cancelLabel={t('cancel')}
        busy={upsert.isPending}
        testId="admin-flag-disable-dialog"
        confirmTestId="admin-flag-disable-confirm"
        onConfirm={() => {
          if (!pendingDisable) return;
          upsert.mutate(
            {
              key: pendingDisable.key,
              description: pendingDisable.description,
              enabledGlobal: false,
            },
            {
              onSuccess: () => {
                setPendingDisable(null);
                toast({ title: t('updated') });
              },
              onError: (error) =>
                toast({
                  variant: 'destructive',
                  title: t('errorTitle'),
                  description: getErrorMessage(error),
                }),
            },
          );
        }}
      />
    </div>
  );
}
