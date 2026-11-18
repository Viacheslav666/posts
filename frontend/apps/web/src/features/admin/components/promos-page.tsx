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
  useAdminPromos,
  useCreatePromoMutation,
  useIsPlatformAdmin,
} from '../hooks/use-admin-queries';

export function AdminPromosPage() {
  const t = useTranslations('admin.promos');
  const { isPlatformAdmin } = useIsPlatformAdmin();
  const query = useAdminPromos(isPlatformAdmin);
  const create = useCreatePromoMutation();

  const [code, setCode] = useState('');
  const [value, setValue] = useState('10');
  const [kind, setKind] = useState<'percent' | 'fixed' | 'trial_days'>('percent');

  const onCreate = () => {
    const now = new Date();
    const until = new Date(Date.now() + 30 * 86400000);
    create.mutate(
      {
        code: code.trim().toUpperCase(),
        kind,
        value: Number(value),
        validFrom: now.toISOString(),
        validUntil: until.toISOString(),
        active: true,
        oncePerWorkspace: true,
        stackable: false,
      },
      {
        onSuccess: () => {
          setCode('');
          toast({ title: t('created') });
        },
        onError: (error) =>
          toast({
            variant: 'destructive',
            title: t('errorTitle'),
            description: getErrorMessage(error),
          }),
      },
    );
  };

  return (
    <div className="space-y-6" data-testid="admin-promos-page">
      <div>
        <h1 className="font-display text-3xl font-semibold tracking-tight">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <section className="space-y-3 rounded-xl border border-border p-4" data-testid="admin-promo-create">
        <h2 className="font-semibold">{t('createTitle')}</h2>
        <div className="grid gap-2 sm:grid-cols-3">
          <Input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder={t('code')}
            data-testid="admin-promo-code"
            aria-label={t('code')}
          />
          <Input
            value={value}
            onChange={(e) => setValue(e.target.value)}
            type="number"
            placeholder={t('value')}
            data-testid="admin-promo-value"
            aria-label={t('value')}
          />
          <select
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={kind}
            onChange={(e) => setKind(e.target.value as typeof kind)}
            data-testid="admin-promo-kind"
            aria-label={t('kind')}
          >
            <option value="percent">{t('kinds.percent')}</option>
            <option value="fixed">{t('kinds.fixed')}</option>
            <option value="trial_days">{t('kinds.trial_days')}</option>
          </select>
        </div>
        <Button
          type="button"
          disabled={code.trim().length < 2 || create.isPending}
          onClick={onCreate}
          data-testid="admin-promo-create-submit"
        >
          {t('create')}
        </Button>
      </section>

      {query.isLoading ? (
        <Skeleton className="h-32 w-full" />
      ) : query.isError ? (
        <EmptyState title={t('errorTitle')} description={t('errorBody')} />
      ) : !query.data?.items?.length ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border" data-testid="admin-promos-table">
          <table className="w-full min-w-[36rem] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-3 py-2 font-medium">{t('code')}</th>
                <th className="px-3 py-2 font-medium">{t('kind')}</th>
                <th className="px-3 py-2 font-medium">{t('value')}</th>
                <th className="px-3 py-2 font-medium">{t('status')}</th>
              </tr>
            </thead>
            <tbody>
              {query.data.items.map((promo) => (
                <tr key={promo.id} className="border-b border-border/70 last:border-0">
                  <td className="px-3 py-2 font-mono">{promo.code}</td>
                  <td className="px-3 py-2">{promo.kind}</td>
                  <td className="px-3 py-2">{promo.value}</td>
                  <td className="px-3 py-2">
                    {promo.active ? (
                      <Badge variant="secondary">{t('active')}</Badge>
                    ) : (
                      <Badge variant="outline">{t('inactive')}</Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
