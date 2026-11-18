'use client';

import {
  Badge,
  Button,
  EmptyState,
  Input,
  Skeleton,
  toast,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import {
  useAdminAnnouncements,
  useCreateAnnouncementMutation,
  useIsPlatformAdmin,
} from '../hooks/use-admin-queries';

export function AdminAnnouncementsPage() {
  const t = useTranslations('admin.announcements');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminAnnouncements(isPlatformAdmin);
  const create = useCreateAnnouncementMutation();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [severity, setSeverity] = useState<'info' | 'warning' | 'critical'>('info');

  return (
    <div className="space-y-6" data-testid="admin-announcements-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <section className="space-y-3 rounded-xl border border-border p-4" data-testid="admin-announcement-create">
        <h2 className="font-semibold">{t('createTitle')}</h2>
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={t('fieldTitle')}
          data-testid="admin-announcement-title"
          aria-label={t('fieldTitle')}
        />
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={t('fieldBody')}
          className="min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
          data-testid="admin-announcement-body"
          aria-label={t('fieldBody')}
        />
        <select
          className="h-10 rounded-md border border-input bg-background px-3 text-sm"
          value={severity}
          onChange={(e) => setSeverity(e.target.value as typeof severity)}
          data-testid="admin-announcement-severity"
          aria-label={t('severity')}
        >
          <option value="info">{t('severities.info')}</option>
          <option value="warning">{t('severities.warning')}</option>
          <option value="critical">{t('severities.critical')}</option>
        </select>
        <Button
          type="button"
          disabled={!title.trim() || !body.trim() || create.isPending}
          onClick={() => {
            create.mutate(
              {
                title: title.trim(),
                body: body.trim(),
                severity,
                active: true,
              },
              {
                onSuccess: () => {
                  setTitle('');
                  setBody('');
                  toast({ title: t('published') });
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
          data-testid="admin-announcement-publish"
        >
          {t('publish')}
        </Button>
      </section>

      {query.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.items?.length ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <ul className="space-y-3" role="list" data-testid="admin-announcements-list">
          {query.data.items.map((item) => (
            <li key={item.id} className="rounded-lg border border-border p-4">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-semibold">{item.title}</h3>
                <Badge variant="secondary">{item.severity}</Badge>
                {item.active ? <Badge>{t('active')}</Badge> : null}
              </div>
              <p className="mt-1 text-sm text-muted-foreground">{item.body}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
